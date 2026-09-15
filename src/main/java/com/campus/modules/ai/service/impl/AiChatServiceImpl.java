package com.campus.modules.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import com.campus.modules.ai.agent.tool.ToolRegistry;
import com.campus.modules.ai.config.AiConfig;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.service.AiChatService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.util.DataDesensitizer;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.entity.QaKnowledge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 客服流式对话业务实现类 (基于 Function Calling 调度引擎与本地智能双模架构)
 * 对应《智能交互与凭证中心实施规划》第二部分架构设计与任务一
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final QaKnowledgeService qaKnowledgeService;
    private final ToolRegistry toolRegistry;
    private final StudentMapper studentMapper;
    private final AiConfig aiConfig;

    /** 共享的高性能 HttpClient 实例 (强制 HTTP/1.1 避免 Docker/代理环境 HTTP/2 ALPN 偶发握手超时) */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 用于流式推送的轻量线程池 */
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-chat-sse");
        t.setDaemon(true);
        return t;
    });

    @Override
    public SseEmitter streamChat(AiChatRequest request, Long studentId) {
        // 创建 120 秒超时的 SseEmitter
        SseEmitter emitter = new SseEmitter(120_000L);

        emitter.onCompletion(() -> log.debug("SSE 对话连接正常关闭"));
        emitter.onTimeout(() -> {
            log.warn("SSE 对话连接超时");
            emitter.complete();
        });
        emitter.onError(e -> log.error("SSE 对话发生异常: {}", e.getMessage()));

        sseExecutor.submit(() -> {
            try {
                processChatStream(request, studentId, emitter);
            } catch (Exception e) {
                log.error("处理 AI 问答流时发生未捕获异常", e);
                sendChunk(emitter, "\n\n【系统提示】AI 响应过程遇到异常，请稍后重试。", true, "ERROR", null);
                sendDone(emitter);
            }
        });

        return emitter;
    }

    /**
     * 核心流式调度与 Agent 循环执行器 (Agent Loop)
     */
    private void processChatStream(AiChatRequest request, Long studentId, SseEmitter emitter) {
        String rawQuestion = request != null ? request.getQuestion() : "";
        if (!StringUtils.hasText(rawQuestion)) {
            sendChunk(emitter, "您好！我是校园活动小助手“小智”，请问有什么可以帮您的吗？", true, "SYSTEM", null);
            sendDone(emitter);
            return;
        }

        // 1. 敏感数据脱敏过滤器 (DataDesensitizer)
        String cleanQuestion = DataDesensitizer.desensitize(rawQuestion.trim());
        log.info("【AI 对话】收到提问: [学生ID:{}] '{}' -> 脱敏后: '{}'", studentId, rawQuestion, cleanQuestion);

        // 2. 检查是否为完全无需调用工具的强置信度高频官方标准问答
        boolean personalQuery = isPersonalQuery(cleanQuestion);
        if (!personalQuery) {
            QaKnowledge hit = qaKnowledgeService.matchKnowledge(cleanQuestion);
            if (hit != null) {
                qaKnowledgeService.incrementViewCount(hit.getId());
                streamKnowledgeAnswer(emitter, hit);
                sendDone(emitter);
                return;
            }
        }

        // 3. 优先调度商汤日日新 / OpenAI 兼容大模型 Function Calling 引擎
        if (aiConfig.isEnabled() && StringUtils.hasText(aiConfig.getApiKey())) {
            boolean llmSuccess = runLlmAgentLoop(cleanQuestion, studentId, emitter);
            if (llmSuccess) {
                sendDone(emitter);
                return;
            }
            log.warn("外部大模型接口调用失败或网络不可达，自动无缝降级为本地智能工具引擎 (Local Agent)");
        }

        // 4. 本地智能工具引擎兜底 (Local Agent Fallback: 同样支持 5 大只读工具执行与轨迹展示)
        streamLocalAgentAnswer(emitter, cleanQuestion, studentId);
        sendDone(emitter);
    }

    /**
     * 执行大模型两轮握手 Agent 循环 (Function Calling Loop)
     * 第一轮: 提交提问与可用工具集契约，大模型自主判定是否下发 tool_calls
     * 第二轮: 若大模型触发工具，后端在安全隔离下执行并将 Fact JSON 回传大模型流式组织最终解答
     */
    private boolean runLlmAgentLoop(String cleanQuestion, Long studentId, SseEmitter emitter) {
        try {
            String endpoint = normalizeEndpoint(aiConfig.getBaseUrl());

            // -----------------------------------------------------------------
            // 第一轮请求 (Round 1): 工具决策判定 (stream = false)
            // -----------------------------------------------------------------
            JSONObject round1Body = new JSONObject();
            round1Body.put("model", aiConfig.getModel());
            round1Body.put("stream", false);
            if (aiConfig.getMaxTokens() != null) {
                round1Body.put("max_tokens", aiConfig.getMaxTokens());
            }
            if (aiConfig.getTemperature() != null) {
                round1Body.put("temperature", aiConfig.getTemperature());
            }
            if ("none".equalsIgnoreCase(aiConfig.getReasoningEffort()) || (aiConfig.getModel() != null && aiConfig.getModel().toLowerCase().contains("qwen"))) {
                round1Body.put("enable_thinking", false);
            }

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", buildSystemPrompt());
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", cleanQuestion);
            messages.add(userMsg);

            round1Body.put("messages", messages);
            round1Body.put("tools", toolRegistry.getOpenAiToolDefinitions());
            round1Body.put("tool_choice", "auto");

            HttpRequest round1Req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(round1Body.toJSONString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> round1Resp;
            try {
                round1Resp = httpClient.send(round1Req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.warn("大模型 Round 1 握手首次连接超时或中断，立即发起一次快速重试: {}", ex.getMessage());
                round1Resp = httpClient.send(round1Req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }

            if (round1Resp.statusCode() != 200) {
                log.warn("大模型 Round 1 握手返回非200状态: status={}, body={}", round1Resp.statusCode(), round1Resp.body());
                return false;
            }

            JSONObject respJson = JSON.parseObject(round1Resp.body());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("大模型 Round 1 未返回有效 choices");
                return false;
            }

            JSONObject choice0 = choices.getJSONObject(0);
            JSONObject assistantMsg = choice0.getJSONObject("message");
            if (assistantMsg == null) {
                return false;
            }

            JSONArray toolCalls = assistantMsg.getJSONArray("tool_calls");

            String providerTag = StringUtils.hasText(aiConfig.getProvider()) ? aiConfig.getProvider().toUpperCase() : "LLM";

            // 情况 A: 大模型判定无需调用工具，直接给出回答
            if (toolCalls == null || toolCalls.isEmpty()) {
                String directAnswer = assistantMsg.getString("content");
                if (StringUtils.hasText(directAnswer)) {
                    streamDirectText(emitter, directAnswer, providerTag);
                    return true;
                }
                return false;
            }

            // 情况 B: 大模型判定需要调用工具 (Function Calling)
            log.info("【大模型决策触发 Function Calling】下发工具数量: {}", toolCalls.size());

            JSONArray round2Messages = new JSONArray();
            round2Messages.addAll(messages);
            round2Messages.add(assistantMsg); // 追加包含 tool_calls 的 assistant 消息

            // 逐个执行工具并组装 tool 响应
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject tc = toolCalls.getJSONObject(i);
                String callId = tc.getString("id");
                JSONObject func = tc.getJSONObject("function");
                String funcName = func != null ? func.getString("name") : "";
                String argsStr = func != null ? func.getString("arguments") : "{}";

                // 向客户端推送工具调用开始事件
                JSONObject startEvent = new JSONObject();
                startEvent.put("toolCallId", callId);
                startEvent.put("toolName", funcName);
                startEvent.put("status", "CALLING");
                startEvent.put("arguments", argsStr);
                sendChunk(emitter, "", false, "TOOL_AGENT", startEvent);

                // 强制注入 studentId 安全执行工具
                ToolExecutionRecord record = toolRegistry.executeTool(callId, funcName, argsStr, studentId);

                // 向客户端推送工具调用完成事件 (携带事实 JSON 与耗时)
                JSONObject doneEvent = new JSONObject();
                doneEvent.put("toolCallId", callId);
                doneEvent.put("toolName", funcName);
                doneEvent.put("status", record.getStatus());
                doneEvent.put("description", record.getDescription());
                doneEvent.put("arguments", argsStr);
                doneEvent.put("resultFactJson", record.getResultFactJson());
                doneEvent.put("executionTimeMs", record.getExecutionTimeMs());
                sendChunk(emitter, "", false, "TOOL_AGENT", doneEvent);

                // 组装 role: tool 消息
                JSONObject toolResultMsg = new JSONObject();
                toolResultMsg.put("role", "tool");
                toolResultMsg.put("tool_call_id", callId);
                toolResultMsg.put("name", funcName);
                toolResultMsg.put("content", record.getResultFactJson());
                round2Messages.add(toolResultMsg);
            }

            // -----------------------------------------------------------------
            // 第二轮请求 (Round 2): 大模型基于 Fact JSON 组织最终自然语言解答 (stream = true)
            // -----------------------------------------------------------------
            JSONObject round2Body = new JSONObject();
            round2Body.put("model", aiConfig.getModel());
            round2Body.put("stream", true);
            if (aiConfig.getMaxTokens() != null) {
                round2Body.put("max_tokens", aiConfig.getMaxTokens());
            }
            if (aiConfig.getTemperature() != null) {
                round2Body.put("temperature", aiConfig.getTemperature());
            }
            if ("none".equalsIgnoreCase(aiConfig.getReasoningEffort()) || (aiConfig.getModel() != null && aiConfig.getModel().toLowerCase().contains("qwen"))) {
                round2Body.put("enable_thinking", false);
            }
            round2Body.put("messages", round2Messages);

            HttpRequest round2Req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(round2Body.toJSONString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> round2Resp;
            try {
                round2Resp = httpClient.send(round2Req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception ex) {
                log.warn("大模型 Round 2 流式连接首次超时或中断，立即发起一次快速重试: {}", ex.getMessage());
                round2Resp = httpClient.send(round2Req, HttpResponse.BodyHandlers.ofInputStream());
            }

            if (round2Resp.statusCode() != 200) {
                log.warn("大模型 Round 2 流式生成返回非200状态: status={}", round2Resp.statusCode());
                return false;
            }

            // 读取流式 SSE 字符并推给前端
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(round2Resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }
                    if (line.equals("data: [DONE]") || line.equals("data:[DONE]")) {
                        break;
                    }
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring(5).trim();
                        try {
                            JSONObject obj = JSON.parseObject(jsonStr);
                            JSONArray cList = obj.getJSONArray("choices");
                            if (cList != null && !cList.isEmpty()) {
                                JSONObject delta = cList.getJSONObject(0).getJSONObject("delta");
                                if (delta != null && delta.containsKey("content")) {
                                    String text = delta.getString("content");
                                    if (StringUtils.hasText(text)) {
                                        sendChunk(emitter, text, false, providerTag, null);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("执行大模型 Agent 循环时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 本地智能工具引擎兜底 (Local Agent Fallback)
     * 在离线、无外网或无商汤 Key 时，仍然无缝触发只读工具，产出真实数据与轨迹
     */
    private void streamLocalAgentAnswer(SseEmitter emitter, String question, Long studentId) {
        String q = question.toLowerCase();
        boolean queryPoints = q.contains("积分") || q.contains("分") || q.contains("余额") || q.contains("流水") || q.contains("明细");
        boolean queryOrders = q.contains("礼品") || q.contains("兑换") || q.contains("订单") || q.contains("提货") || q.contains("核销");
        boolean queryActivities = q.contains("活动") || q.contains("打卡") || q.contains("报名") || q.contains("候补") || q.contains("排队");
        boolean queryCertificates = q.contains("证书") || q.contains("结项") || q.contains("荣誉") || q.contains("证明") || q.contains("验真");

        if (studentId == null && (queryPoints || queryOrders || queryActivities || queryCertificates)) {
            String loginNotice = "同学你好！你咨询的内容涉及**个人私有业务数据**（如个人积分、兑换订单、活动报名或荣誉证书）。\n\n" +
                    "⚠️ **登录提示**：当前未检测到你的学生登录凭据，请前往【个人中心】完成账号登录，登录后小智即可为你实时查询个人专属数据！";
            streamDirectText(emitter, loginNotice, "LOCAL_AGENT");
            return;
        }

        String answer;

        if (queryPoints) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_pts_" + System.currentTimeMillis(),
                    "query_my_points", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildPointsAnswer(record);
        } else if (queryOrders) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_ord_" + System.currentTimeMillis(),
                    "query_my_orders", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildOrdersAnswer(record);
        } else if (queryActivities) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_act_" + System.currentTimeMillis(),
                    "query_my_activities", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildActivitiesAnswer(record);
        } else if (queryCertificates) {
            ToolExecutionRecord record = toolRegistry.executeTool("local_cert_" + System.currentTimeMillis(),
                    "query_my_certificates", "{\"limit\": 5}", studentId);
            emitToolProgress(emitter, record);
            answer = buildCertificatesAnswer(record);
        } else {
            // 知识检索工具
            JSONObject args = new JSONObject();
            args.put("keyword", question);
            ToolExecutionRecord record = toolRegistry.executeTool("local_qa_" + System.currentTimeMillis(),
                    "search_platform_knowledge", args.toJSONString(), studentId);
            emitToolProgress(emitter, record);
            answer = buildKnowledgeAnswer(record, question);
        }

        // 打字机流式输出
        streamDirectText(emitter, answer, "LOCAL_AGENT");
    }

    private void emitToolProgress(SseEmitter emitter, ToolExecutionRecord record) {
        if (record == null) return;
        JSONObject event = new JSONObject();
        event.put("toolCallId", record.getToolCallId());
        event.put("toolName", record.getToolName());
        event.put("description", record.getDescription());
        event.put("status", record.getStatus());
        event.put("arguments", record.getArgumentsJson());
        event.put("resultFactJson", record.getResultFactJson());
        event.put("executionTimeMs", record.getExecutionTimeMs());
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
            sb.append(String.format("同学你好！已为你实时查询到平台积分账户数据：\n\n" +
                    "• **可用积分余额**：%d pts\n" +
                    "• **历史累计总积分**：%d pts\n\n", available, total));

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
            sb.append(String.format("同学你好！为你查询到商城礼品兑换订单记录：\n\n" +
                    "你当前共有 **%d** 笔处于【待提货/待核销】状态的礼品订单。\n\n", pending));

            if (orders != null && !orders.isEmpty()) {
                sb.append("📦 **最新订单明细**：\n");
                for (int i = 0; i < orders.size(); i++) {
                    JSONObject o = orders.getJSONObject(i);
                    sb.append(String.format("• **【%s】** x %d 件\n" +
                                    "  - 订单编号：`%s`\n" +
                                    "  - 消耗积分：%d pts\n" +
                                    "  - 提货核销码：`%s`\n" +
                                    "  - 当前状态：%s\n" +
                                    "  - 下单时间：%s\n\n",
                            o.getString("giftName"),
                            o.getIntValue("quantity"),
                            o.getString("orderNo"),
                            o.getIntValue("pointsCost"),
                            o.getString("pickupCode"),
                            o.getString("statusText"),
                            o.getString("createTime")));
                }
                sb.append("📍 **领取方式**：请凭上述**提货核销码**前往学工综合服务台，交由负责老师现场核销领货即可。");
            } else {
                sb.append("💡 你近期暂无礼品兑换订单记录，可前往【积分商城】随心选购！");
            }
            return sb.toString();
        } catch (Exception e) {
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
                    sb.append(String.format("%d. **【%s】**（奖励积分: %d pts）\n" +
                                    "   - 参与状态：%s\n" +
                                    "   - 活动地点：%s\n" +
                                    "   - 报名时间：%s\n\n",
                            i + 1,
                            a.getString("activityTitle"),
                            a.getIntValue("rewardPoints"),
                            a.getString("statusText"),
                            a.getString("locationName"),
                            a.getString("signupTime")));
                }
                sb.append("💡 **打卡指引**：参加活动时请保持手机定位开启，现场扫描辅导员出示的加密动态二维码即可完成签到积分奖励！");
            } else {
                sb.append("💡 你当前暂无正在参与或候补的活动，欢迎前往【活动大厅】报名感兴趣的活动！");
            }
            return sb.toString();
        } catch (Exception e) {
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
            sb.append(String.format("同学你好！已为你查询到获得的荣誉结项证书记录：\n\n" +
                    "你目前累计获得 **%d** 张官方结项证书。\n\n", total));

            if (certs != null && !certs.isEmpty()) {
                for (int i = 0; i < certs.size(); i++) {
                    JSONObject c = certs.getJSONObject(i);
                    sb.append(String.format("🏅 **%s**\n" +
                                    "  - 对应活动：【%s】\n" +
                                    "  - 唯一防伪编号：`%s`\n" +
                                    "  - 签发单位：%s\n" +
                                    "  - 签发日期：%s\n" +
                                    "  - 证书状态：%s\n\n",
                            c.getString("title"),
                            c.getString("activityTitle"),
                            c.getString("certificateNo"),
                            c.getString("issuer"),
                            c.getString("issueTime"),
                            c.getString("statusText")));
                }
                sb.append("🛡️ **防伪验真说明**：每张证书具备全校唯一的编号，任何人在移动端或公众核验页面输入编号即可免登录公开验真。");
            } else {
                sb.append("💡 暂未查询到荣誉证书。参加带有【颁发结项证书】标识的校园活动并完成现场打卡后，系统将自动为你签发！");
            }
            return sb.toString();
        } catch (Exception e) {
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
        } catch (Exception ignored) {
        }

        return "同学你好！我是校园活动小助手小智。\n\n" +
                "你可以随时咨询我关于：\n" +
                "• **个人积分与变动明细**（查我的积分）\n" +
                "• **礼品兑换与提货核销码**（查我的订单）\n" +
                "• **校园活动报名、现场GPS打卡及排队候补**（查我的活动）\n" +
                "• **荣誉电子结项证书及公开验真编号**（查我的证书）\n" +
                "• **知识库各类规则解答**\n\n" +
                "请问有什么具体问题可以帮您？";
    }

    private boolean isPersonalQuery(String question) {
        if (!StringUtils.hasText(question)) return false;
        String q = question.toLowerCase();
        return q.contains("我") || q.contains("咱") || q.contains("自己") || q.contains("本人")
                || q.contains("余额") || q.contains("多少分") || q.contains("剩多少") || q.contains("有几")
                || q.contains("查查") || q.contains("帮我查") || q.contains("提货码") || q.contains("我的");
    }

    private void streamKnowledgeAnswer(SseEmitter emitter, QaKnowledge item) {
        String prefix = "【官方知识库解答】\n\n";
        sendChunk(emitter, prefix, false, "KNOWLEDGE_BASE", null);
        sleep(50);

        String answer = item.getAnswer();
        streamDirectText(emitter, answer, "KNOWLEDGE_BASE");
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

    private String buildSystemPrompt() {
        return "你是【校园活动积分平台】专属智能客服助理“小智”。\n" +
                "你的职责是热情、准确、严谨地解答学生关于校园活动、现场打卡、积分变动、礼品兑换、荣誉证书及平台规则的咨询。\n\n" +
                "【核心工具调用准则 (Function Calling)】：\n" +
                "1. 当用户询问个人私有数据（如：“查我的积分/还有多少积分/收支明细”、“查我的礼品订单/提货码”、“查我报名的活动/打卡/候补状态”、“查我获得的荣誉证书”）时，你必须主动调用对应的工具函数（如 query_my_points, query_my_orders, query_my_activities, query_my_certificates）获取最新真实事实。\n" +
                "2. 当需要查询通用打卡、积分、选拔或评奖规则时，可调用 search_platform_knowledge 检索官方解答。\n" +
                "3. 获得工具返回的业务事实 JSON 数据后，必须严格以该事实为依据，组织结构清晰、温暖热情的 Markdown 答复。严禁凭空捏造私有数据。\n" +
                "4. 若工具返回学生未登录或档案不存在，请礼貌提示用户先在【个人中心】完成账号登录。";
    }

    private String normalizeEndpoint(String baseUrl) {
        String endpoint = baseUrl != null ? baseUrl.trim() : "https://api.sensenova.cn/compatible-mode/v1";
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (!endpoint.endsWith("/chat/completions")) {
            endpoint = endpoint + "/chat/completions";
        }
        return endpoint;
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

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            log.debug("完成 SSE 对话流失败: {}", e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
