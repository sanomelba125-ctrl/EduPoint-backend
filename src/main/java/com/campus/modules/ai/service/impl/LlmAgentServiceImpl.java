package com.campus.modules.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import com.campus.modules.ai.agent.tool.ToolRegistry;
import com.campus.modules.ai.client.LlmClient;
import com.campus.modules.ai.client.LlmClientFactory;
import com.campus.modules.ai.client.dto.LlmChatRequest;
import com.campus.modules.ai.client.dto.LlmChatResponse;
import com.campus.modules.ai.client.dto.LlmStreamConsumer;
import com.campus.modules.ai.config.AiConfig;
import com.campus.modules.ai.service.AiMessageService;
import com.campus.modules.ai.service.LlmAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 大模型 Agent 调度与工具协同执行领域服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAgentServiceImpl implements LlmAgentService {

    private final LlmClientFactory llmClientFactory;
    private final ToolRegistry toolRegistry;
    private final AiConfig aiConfig;
    private final AiMessageService aiMessageService;

    @Override
    public boolean runLlmAgentLoop(String cleanQuestion, Long studentId, String conversationId, SseEmitter emitter) {
        LlmClient client = llmClientFactory.getPrimaryClient();
        if (!client.isAvailable()) {
            log.warn("当前模型客户端 [{}] 暂不可用，放弃大模型握手", client.getProvider());
            return false;
        }

        String providerTag = client.getProvider().toUpperCase();

        try {
            // -----------------------------------------------------------------
            // 第一轮请求 (Round 1): 工具决策判定 (stream = false)
            // -----------------------------------------------------------------
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
            messages.add(Map.of("role", "user", "content", cleanQuestion));

            LlmChatRequest round1Request = new LlmChatRequest(
                    aiConfig.getModel(),
                    messages,
                    toolRegistry.getPublicToolDefinitions(),
                    "auto",
                    aiConfig.getTemperature(),
                    aiConfig.getMaxTokens(),
                    aiConfig.getReasoningEffort(),
                    false
            );

            LlmChatResponse round1Resp = client.chat(round1Request);
            if (!round1Resp.success()) {
                log.warn("大模型 Round 1 握手失败: statusCode={}, rawBody={}", round1Resp.statusCode(), round1Resp.rawBody());
                return false;
            }

            // 情况 A: 大模型判定无需调用工具，直接给出回答
            if (!round1Resp.hasToolCalls()) {
                String directAnswer = round1Resp.content();
                if (StringUtils.hasText(directAnswer)) {
                    streamDirectText(emitter, directAnswer, providerTag);
                    aiMessageService.saveMessage(studentId, conversationId, "assistant", directAnswer, providerTag, null);
                    return true;
                }
                return false;
            }

            // 情况 B: 大模型判定需要调用工具 (Function Calling)
            List<Map<String, Object>> toolCalls = round1Resp.toolCalls();
            int maxToolCalls = Math.min(toolCalls.size(), 3);
            log.info("【大模型决策触发 Function Calling】下发工具数: {}，执行上限: {}", toolCalls.size(), maxToolCalls);

            List<Map<String, Object>> round2Messages = new ArrayList<>(messages);
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("tool_calls", toolCalls);
            round2Messages.add(assistantMsg);

            JSONObject lastExecutedToolRecord = null;
            List<ToolExecutionRecord> executedRecords = new ArrayList<>();

            for (int i = 0; i < maxToolCalls; i++) {
                Map<String, Object> tc = toolCalls.get(i);
                String callId = (String) tc.get("id");
                Map<?, ?> func = (Map<?, ?>) tc.get("function");
                String funcName = func != null ? (String) func.get("name") : "";
                String argsStr = func != null ? (String) func.get("arguments") : "{}";

                // 安全校验: 只能调用只读公开工具
                if (!toolRegistry.isPublicTool(funcName)) {
                    log.warn("【安全拦截】大模型试图调用非公开工具: {}，已拒绝", funcName);
                    Map<String, Object> rejectResult = Map.of(
                            "role", "tool",
                            "tool_call_id", callId,
                            "name", funcName,
                            "content", "{\"success\":false,\"error\":\"该工具为个人查询工具，已由本地安全隔离处理\"}"
                    );
                    round2Messages.add(rejectResult);
                    continue;
                }

                // 推送工具调用开始事件
                JSONObject startEvent = new JSONObject();
                startEvent.put("toolCallId", callId);
                startEvent.put("toolName", funcName);
                startEvent.put("status", "CALLING");
                startEvent.put("arguments", argsStr);
                sendChunk(emitter, "", false, "TOOL_AGENT", startEvent);

                // 强制注入 studentId 安全执行工具
                ToolExecutionRecord record = toolRegistry.executeTool(callId, funcName, argsStr, studentId);
                executedRecords.add(record);

                // 推送工具调用完成事件
                JSONObject doneEvent = new JSONObject();
                doneEvent.put("toolCallId", callId);
                doneEvent.put("toolName", funcName);
                doneEvent.put("status", record.getStatus());
                doneEvent.put("description", record.getDescription());
                doneEvent.put("arguments", argsStr);
                doneEvent.put("resultFactJson", record.getResultFactJson());
                doneEvent.put("executionTimeMs", record.getExecutionTimeMs());
                sendChunk(emitter, "", false, "TOOL_AGENT", doneEvent);

                lastExecutedToolRecord = doneEvent;

                // 组装 role: tool 消息
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("name", funcName);
                toolMsg.put("content", record.getResultFactJson());
                round2Messages.add(toolMsg);
            }

            // -----------------------------------------------------------------
            // 第二轮请求 (Round 2): 大模型基于 Fact JSON 组织最终自然语言解答 (stream = true)
            // -----------------------------------------------------------------
            LlmChatRequest round2Request = new LlmChatRequest(
                    aiConfig.getModel(),
                    round2Messages,
                    null,
                    null,
                    aiConfig.getTemperature(),
                    aiConfig.getMaxTokens(),
                    aiConfig.getReasoningEffort(),
                    true
            );

            StringBuilder fullAnswerBuilder = new StringBuilder();
            AtomicBoolean streamSuccess = new AtomicBoolean(true);

            client.streamChat(round2Request, new LlmStreamConsumer() {
                @Override
                public void onChunk(String deltaText, boolean finish) {
                    if (StringUtils.hasText(deltaText)) {
                        fullAnswerBuilder.append(deltaText);
                        sendChunk(emitter, deltaText, finish, providerTag, null);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.warn("大模型 Round 2 流式返回异常: {}，无缝降级为本地事实卡片", throwable.getMessage());
                    streamSuccess.set(false);
                }

                @Override
                public void onComplete() {
                    log.debug("大模型 Round 2 流式完成");
                }
            });

            if (!streamSuccess.get() || fullAnswerBuilder.length() == 0) {
                log.warn("Round 2 未生成有效文本，使用本地模板展示已查事实");
                streamToolResultsFallback(emitter, executedRecords);
                return true;
            }

            // 持久化大模型最终答复与工具执行记录
            String toolCallJson = lastExecutedToolRecord != null ? lastExecutedToolRecord.toJSONString() : null;
            aiMessageService.saveMessage(studentId, conversationId, "assistant", fullAnswerBuilder.toString(), providerTag, toolCallJson);

            return true;
        } catch (Exception e) {
            log.error("执行大模型 Agent 循环时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void streamLocalAgentAnswer(SseEmitter emitter, String question, Long studentId, String conversationId) {
        String q = question.toLowerCase();
        boolean queryPoints = q.contains("积分") || q.contains("分") || q.contains("余额") || q.contains("流水") || q.contains("明细");
        boolean queryOrders = q.contains("礼品") || q.contains("兑换") || q.contains("订单") || q.contains("提货") || q.contains("核销");
        boolean queryActivities = q.contains("活动") || q.contains("打卡") || q.contains("报名") || q.contains("候补") || q.contains("排队");
        boolean queryCertificates = q.contains("证书") || q.contains("结项") || q.contains("荣誉") || q.contains("证明") || q.contains("验真");

        if (studentId == null && (queryPoints || queryOrders || queryActivities || queryCertificates)) {
            String loginNotice = """
                    同学你好！你咨询的内容涉及**个人私有业务数据**（如个人积分、兑换订单、活动报名或荣誉证书）。

                    ⚠️ **登录提示**：当前未检测到你的学生登录凭据，请前往【个人中心】完成账号登录，登录后小智即可为你实时查询个人专属数据！""";
            streamDirectText(emitter, loginNotice, "LOCAL_AGENT");
            aiMessageService.saveMessage(studentId, conversationId, "assistant", loginNotice, "LOCAL_AGENT", null);
            return;
        }

        String answer;
        JSONObject toolEvent = null;

        if (queryPoints) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_pts_" + System.currentTimeMillis(),
                    "query_my_points", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildPointsAnswer(record);
            toolEvent = toToolEvent(record);
        } else if (queryOrders) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_ord_" + System.currentTimeMillis(),
                    "query_my_orders", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildOrdersAnswer(record);
            toolEvent = toToolEvent(record);
        } else if (queryActivities) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_act_" + System.currentTimeMillis(),
                    "query_my_activities", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildActivitiesAnswer(record);
            toolEvent = toToolEvent(record);
        } else if (queryCertificates) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_cert_" + System.currentTimeMillis(),
                    "query_my_certificates", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildCertificatesAnswer(record);
            toolEvent = toToolEvent(record);
        } else {
            JSONObject args = new JSONObject();
            args.put("keyword", question);
            ToolExecutionRecord record = toolRegistry.executeTool("local_qa_" + System.currentTimeMillis(),
                    "search_platform_knowledge", args.toJSONString(), studentId);
            emitToolProgress(emitter, record);
            answer = buildKnowledgeAnswer(record, question);
            toolEvent = toToolEvent(record);
        }

        streamDirectText(emitter, answer, "LOCAL_AGENT");

        String toolCallJson = toolEvent != null ? toolEvent.toJSONString() : null;
        aiMessageService.saveMessage(studentId, conversationId, "assistant", answer, "LOCAL_AGENT", toolCallJson);
    }

    @Override
    public void streamToolResultsFallback(SseEmitter emitter, List<ToolExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            streamDirectText(emitter, "暂时无法组织回答，请稍后重试或联系管理员。", "LOCAL_AGENT");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【系统提示】AI 模型响应超时或中断，以下为已查询到的实时业务事实数据：\n\n");
        for (ToolExecutionRecord record : records) {
            if ("SUCCESS".equals(record.getStatus()) && record.getResultFactJson() != null) {
                try {
                    JSONObject json = JSON.parseObject(record.getResultFactJson());
                    if (json.getBooleanValue("success")) {
                        sb.append("📋 **").append(record.getDescription()).append("**\n");
                        sb.append("```json\n").append(json.toJSONString()).append("\n```\n\n");
                    }
                } catch (Exception e) {
                    log.warn("解析降级展示 Fact JSON 异常: {}", e.getMessage());
                    sb.append("📋 ").append(record.getDescription()).append(": ").append(record.getResultFactJson()).append("\n\n");
                }
            }
        }
        sb.append("如需更详细的解读，请稍后重试。");
        streamDirectText(emitter, sb.toString(), "LOCAL_AGENT");
    }

    private JSONObject toToolEvent(ToolExecutionRecord record) {
        if (record == null) return null;
        JSONObject event = new JSONObject();
        event.put("toolCallId", record.getToolCallId());
        event.put("toolName", record.getToolName());
        event.put("description", record.getDescription());
        event.put("status", record.getStatus());
        event.put("arguments", record.getArgumentsJson());
        event.put("resultFactJson", record.getResultFactJson());
        event.put("executionTimeMs", record.getExecutionTimeMs());
        return event;
    }

    private void emitToolProgress(SseEmitter emitter, ToolExecutionRecord record) {
        if (record == null) return;
        JSONObject event = toToolEvent(record);
        sendChunk(emitter, "", false, "TOOL_AGENT", event);
        sleep(50);
    }

    private String buildPointsAnswer(ToolExecutionRecord record) {
        try {
            JSONObject json = JSON.parseObject(record.getResultFactJson());
            if (!json.getBooleanValue("success")) {
                return "【积分查询提示】" + json.getString("error");
            }
            int available = json.getIntValue("currentAvailablePoints");
            int total = json.getIntValue("historicalTotalPoints");
            JSONArray txs = json.getJSONArray("recentTransactions");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("""
                    同学你好！已为你实时查询到平台积分账户数据：

                    • **可用积分余额**：%d pts
                    • **历史累计总积分**：%d pts

                    """, available, total));

            if (txs != null && !txs.isEmpty()) {
                sb.append("📋 **最近收支流水记录**：\n");
                for (int i = 0; i < txs.size(); i++) {
                    JSONObject tx = txs.getJSONObject(i);
                    sb.append(String.format("%d. [%s] **%s pts** - %s (%s)\n",
                            i + 1,
                            tx.getString("typeText"),
                            tx.getString("changePoints"),
                            tx.getString("description"),
                            tx.getString("createTime")));
                }
            } else {
                sb.append("💡 暂无最近积分流水变动记录。\n");
            }
            sb.append("\n💡 提示：积分可用于在【积分兑换商城】兑换精选文创礼品，参加校园活动现场打卡可赚取更多积分哦！");
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建积分回答异常: {}", e.getMessage());
            return "已调取积分数据，详情请前往【个人中心 - 积分明细】查阅。";
        }
    }

    private String buildOrdersAnswer(ToolExecutionRecord record) {
        try {
            JSONObject json = JSON.parseObject(record.getResultFactJson());
            if (!json.getBooleanValue("success")) {
                return "【订单查询提示】" + json.getString("error");
            }
            int pending = json.getIntValue("pendingPickupCount");
            JSONArray orders = json.getJSONArray("orders");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("""
                    同学你好！为你查询到商城礼品兑换订单记录：

                    你当前共有 **%d** 笔处于【待提货/待核销】状态的礼品订单。

                    """, pending));

            if (orders != null && !orders.isEmpty()) {
                sb.append("📦 **最新订单明细**：\n");
                for (int i = 0; i < orders.size(); i++) {
                    JSONObject o = orders.getJSONObject(i);
                    sb.append(String.format("""
                            • **【%s】** x %d 件
                              - 订单编号：`%s`
                              - 消耗积分：%d pts
                              - 提货核销码：`%s`
                              - 当前状态：%s
                              - 下单时间：%s

                            """,
                            o.getString("giftName"),
                            o.getIntValue("quantity"),
                            o.getString("orderNo"),
                            o.getIntValue("pointsCost"),
                            o.getString("pickupCode"),
                            o.getString("statusText"),
                            o.getString("createTime")));
                }
                sb.append("📍 **领取方式**：请凭上述**提货核销码**前往学工综合服务台，交由负责老师现场扫码或输入核销领货即可。");
            } else {
                sb.append("💡 你近期暂无礼品兑换订单记录，可前往【积分商城】随心选购！");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建订单回答异常: {}", e.getMessage());
            return "已调取订单数据，详情请前往【个人中心 - 我的兑换订单】查阅。";
        }
    }

    private String buildActivitiesAnswer(ToolExecutionRecord record) {
        try {
            JSONObject json = JSON.parseObject(record.getResultFactJson());
            if (!json.getBooleanValue("success")) {
                return "【活动查询提示】" + json.getString("error");
            }
            JSONArray acts = json.getJSONArray("activities");

            StringBuilder sb = new StringBuilder();
            sb.append("同学你好！已为你查询到校园活动参与及排队情况：\n\n");

            if (acts != null && !acts.isEmpty()) {
                for (int i = 0; i < acts.size(); i++) {
                    JSONObject a = acts.getJSONObject(i);
                    sb.append(String.format("""
                            %d. **【%s】**（奖励积分: %d pts）
                               - 参与状态：%s
                               - 活动地点：%s
                               - 报名时间：%s

                            """,
                            i + 1,
                            a.getString("activityTitle"),
                            a.getIntValue("rewardPoints"),
                            a.getString("statusText"),
                            a.getString("locationName"),
                            a.getString("signupTime")));
                }
                sb.append("💡 **打卡指引**：参加活动时请保持手机定位开启，现场扫描会场广播大屏展示的动态二维码即可完成签到积分奖励！");
            } else {
                sb.append("💡 你当前暂无正在参与或候补的活动，欢迎前往【活动大厅】报名感兴趣的活动！");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建活动回答异常: {}", e.getMessage());
            return "已调取活动数据，详情请前往【活动大厅 - 我的活动】查阅。";
        }
    }

    private String buildCertificatesAnswer(ToolExecutionRecord record) {
        try {
            JSONObject json = JSON.parseObject(record.getResultFactJson());
            if (!json.getBooleanValue("success")) {
                return "【证书查询提示】" + json.getString("error");
            }
            int total = json.getIntValue("totalCertificateCount");
            JSONArray certs = json.getJSONArray("certificates");

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("""
                    同学你好！已为你查询到获得的荣誉结项证书记录：

                    你目前累计获得 **%d** 张官方结项证书。

                    """, total));

            if (certs != null && !certs.isEmpty()) {
                for (int i = 0; i < certs.size(); i++) {
                    JSONObject c = certs.getJSONObject(i);
                    sb.append(String.format("""
                            🏅 **%s**
                              - 对应活动：【%s】
                              - 唯一防伪编号：`%s`
                              - 签发单位：%s
                              - 签发日期：%s
                              - 证书状态：%s

                            """,
                            c.getString("title"),
                            c.getString("activityTitle"),
                            c.getString("certificateNo"),
                            c.getString("issuer"),
                            c.getString("issueTime"),
                            c.getString("statusText")));
                }
                sb.append("🛡️ **防伪验真说明**：每张证书具备全校唯一的防伪编号，在移动端或公众核验页面输入编号即可免登录公开验真。");
            } else {
                sb.append("💡 暂未查询到荣誉证书。参加带有【颁发结项证书】标识的校园活动并完成现场打卡后，系统将自动为你签发！");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建证书回答异常: {}", e.getMessage());
            return "已调取证书数据，详情请前往【个人中心 - 我的证书】查阅。";
        }
    }

    private String buildKnowledgeAnswer(ToolExecutionRecord record, String question) {
        try {
            JSONObject json = JSON.parseObject(record.getResultFactJson());
            JSONArray faqs = json.getJSONArray("matchedFaqs");
            if (faqs != null && !faqs.isEmpty()) {
                JSONObject first = faqs.getJSONObject(0);
                return String.format("【知识库官方解答 - %s】\n\n%s",
                        first.getString("question"),
                        first.getString("officialAnswer"));
            }
        } catch (Exception e) {
            log.warn("构建知识库回答异常: {}", e.getMessage());
        }

        return """
                同学你好！我是校园活动小助手小智。

                你可以随时咨询我关于：
                • **个人积分与变动明细**（查我的积分）
                • **礼品兑换与提货核销码**（查我的订单）
                • **校园活动报名、现场GPS打卡及排队候补**（查我的活动）
                • **荣誉电子结项证书及公开验真编号**（查我的证书）
                • **知识库各类规则解答**

                请问有什么具体问题可以帮您？""";
    }

    private String buildSystemPrompt() {
        return """
                你是【校园活动积分平台】专属智能客服助理"小智"。
                你的职责是热情、准确、严谨地解答学生关于校园活动、现场打卡、积分变动、礼品兑换及平台规则的咨询。

                【核心工具调用准则 (Function Calling)】：
                1. 当用户询问通用规则与平台解答时，可调用知识库检索工具：search_platform_knowledge 检索官方规则知识。
                2. 获得工具返回的业务事实 JSON 数据后，必须严格以该事实为依据，组织结构清晰、温暖热情的 Markdown 答复。严禁凭空捏造数据。
                3. 工具失败或无结果时如实告知，不要编造数据。
                4. 当用户询问个人私有数据（如个人积分余额、兑换订单、提货码、报名记录、证书等），请回答"个人数据查询已由系统本地安全处理，请查看上方查询结果"。不要尝试调用个人查询工具或编造个人数据。
                5. 问候、感谢和能力介绍等问题通常不需要工具，直接友好回复即可。""";
    }

    private void streamDirectText(SseEmitter emitter, String text, String source) {
        if (!StringUtils.hasText(text)) return;
        int step = 6;
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + step, text.length());
            String chunk = text.substring(i, end);
            boolean isLast = (end == text.length());
            sendChunk(emitter, chunk, isLast, source, null);
            sleep(30);
        }
    }

    private void sendChunk(SseEmitter emitter, String content, boolean finish, String source, JSONObject toolCall) {
        try {
            JSONObject data = new JSONObject();
            data.put("content", content);
            data.put("finish", finish);
            data.put("source", source);
            if (toolCall != null) {
                data.put("toolCall", toolCall);
            }
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(data.toJSONString()));
        } catch (Exception e) {
            log.debug("向客户端推送 SSE chunk 失败(可能客户端已断开): {}", e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
