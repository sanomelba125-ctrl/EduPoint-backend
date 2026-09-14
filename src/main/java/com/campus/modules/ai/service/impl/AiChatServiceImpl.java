package com.campus.modules.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.ai.config.AiConfig;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.service.AiChatService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.util.DataDesensitizer;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.StudentGiftMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 客服流式对话业务实现类
 * 对应时序图: 图4-21 AI 客服 SSE 流式问答时序图
 * 策略: 隐私脱敏 -> 知识库优先检索 -> 私有数据意图增强 -> 大模型流式/本地智能双模兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final QaKnowledgeService qaKnowledgeService;
    private final StudentMapper studentMapper;
    private final StudentGiftMapper studentGiftMapper;
    private final StudentActivityMapper studentActivityMapper;
    private final AiConfig aiConfig;

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
                sendChunk(emitter, "\n\n【系统提示】AI 响应过程遇到异常，请稍后重试。", true, "ERROR");
                sendDone(emitter);
            }
        });

        return emitter;
    }

    /**
     * 核心流式编排
     */
    private void processChatStream(AiChatRequest request, Long studentId, SseEmitter emitter) {
        String rawQuestion = request != null ? request.getQuestion() : "";
        if (!StringUtils.hasText(rawQuestion)) {
            sendChunk(emitter, "您好！我是校园活动小助手，请问有什么可以帮您的吗？", true, "SYSTEM");
            sendDone(emitter);
            return;
        }

        // 1. 敏感数据脱敏过滤器 (DataDesensitizer)
        String cleanQuestion = DataDesensitizer.desensitize(rawQuestion.trim());
        log.info("【AI 对话】收到学生 (ID:{}) 提问: '{}' -> 脱敏后: '{}'", studentId, rawQuestion, cleanQuestion);

        // 2. 意图识别与学生私有数据注入
        boolean personalQuery = isPersonalQuery(cleanQuestion);
        String personalContext = extractStudentContext(cleanQuestion, studentId);

        // 3. 仅在非个人私有数据查询时，检索本地 qa_knowledge 官方通用问答库 (避免通用知识库截胡个人私有数据查询)
        if (!personalQuery) {
            QaKnowledge hit = qaKnowledgeService.matchKnowledge(cleanQuestion);
            if (hit != null) {
                // 本地知识库强匹配命中 (Exact/High-Confidence Hit)
                qaKnowledgeService.incrementViewCount(hit.getId());
                streamKnowledgeAnswer(emitter, hit);
                sendDone(emitter);
                return;
            }
        }

        // 4. 调用商汤日日新大模型 (已注入真实学生上下文：积分、订单、活动)
        if (aiConfig.isEnabled() && StringUtils.hasText(aiConfig.getApiKey())) {
            boolean llmSuccess = streamFromLlm(cleanQuestion, personalContext, emitter);
            if (llmSuccess) {
                sendDone(emitter);
                return;
            }
            log.warn("商汤大模型调用失败或不可达，自动无缝降级为本地智能推断引擎");
        }

        // 5. 本地智能推断兜底 (Fallback / Mock 模式)
        streamLocalSynthesizedAnswer(emitter, cleanQuestion, personalContext, studentId);
        sendDone(emitter);
    }

    /**
     * 判断是否为针对个人私有业务数据的意图 (如查我自己的积分、查我的订单、查我报名的活动)
     */
    private boolean isPersonalQuery(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("我") || q.contains("咱") || q.contains("自己") || q.contains("本人")
                || q.contains("余额") || q.contains("多少分") || q.contains("剩多少") || q.contains("有几")
                || q.contains("查查") || q.contains("帮我查") || q.contains("提货码是多少");
    }

    /**
     * 流式输出知识库标准答案 (平滑打字机动画)
     */
    private void streamKnowledgeAnswer(SseEmitter emitter, QaKnowledge item) {
        String prefix = "【官方知识库解答】\n\n";
        sendChunk(emitter, prefix, false, "KNOWLEDGE_BASE");
        sleep(50);

        String answer = item.getAnswer();
        int step = 6;
        for (int i = 0; i < answer.length(); i += step) {
            int end = Math.min(i + step, answer.length());
            String chunk = answer.substring(i, end);
            boolean isLast = (end == answer.length());
            sendChunk(emitter, chunk, isLast, "KNOWLEDGE_BASE");
            sleep(35);
        }
    }

    /**
     * 识别学生个人数据意图，查询真实业务数据并构建注入上下文
     */
    private String extractStudentContext(String question, Long studentId) {
        if (studentId == null) {
            if (isPersonalQuery(question)) {
                return "【系统提示】：用户提问涉及个人私有业务数据（如查个人积分、查个人订单或活动），但当前未检测到学生登录状态。请礼貌提醒学生先前往平台【个人中心】登录账号，登录后即可为您实时查询个人积分和订单状态。";
            }
            return "";
        }

        String q = question.toLowerCase();
        boolean queryPoints = q.contains("积分") || q.contains("分") || q.contains("余额");
        boolean queryGifts = q.contains("礼品") || q.contains("兑换") || q.contains("订单") || q.contains("提货");
        boolean queryActivities = q.contains("活动") || q.contains("打卡") || q.contains("报名") || q.contains("候补");

        if (!queryPoints && !queryGifts && !queryActivities) {
            return "";
        }

        StringBuilder sb = new StringBuilder("【当前学生真实业务上下文】\n");
        try {
            Student student = studentMapper.selectById(studentId);
            if (student != null) {
                sb.append(String.format("- 学生姓名：%s，学号：%s\n", student.getName(), student.getStudentNo()));
                sb.append(String.format("- 当前可用积分余额：%d 分，历史累计总积分：%d 分\n",
                        student.getCurrentPoints() != null ? student.getCurrentPoints() : 0,
                        student.getTotalPoints() != null ? student.getTotalPoints() : 0));
            }

            if (queryGifts) {
                Long pendingOrders = studentGiftMapper.selectCount(new LambdaQueryWrapper<StudentGift>()
                        .eq(StudentGift::getStudentId, studentId)
                        .eq(StudentGift::getStatus, 1) // 1-待发放/待核销
                        .eq(StudentGift::getDeleted, 0));
                sb.append(String.format("- 待核销礼品订单数：%d 笔\n", pendingOrders));
            }

            if (queryActivities) {
                Long signedUpCount = studentActivityMapper.selectCount(new LambdaQueryWrapper<StudentActivity>()
                        .eq(StudentActivity::getStudentId, studentId)
                        .in(StudentActivity::getStatus, 1, 2) // 1-已报名, 2-已打卡完成
                        .eq(StudentActivity::getDeleted, 0));
                sb.append(String.format("- 累计参与活动总数：%d 场\n", signedUpCount));
            }
        } catch (Exception e) {
            log.warn("提取学生业务上下文失败: studentId={}", studentId, e);
        }

        return sb.toString();
    }

    /**
     * 调用商汤日日新 (SenseNova 6.8 Flash Lite) REST SSE 流式接口
     */
    private boolean streamFromLlm(String cleanQuestion, String personalContext, SseEmitter emitter) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String systemPrompt = "你是【校园活动积分平台】专属智能客服助理“小智”。\n" +
                    "请以热情、礼貌、准确的口吻回答学生。若上下文提供了学生本人的真实积分、订单或活动数据，必须以该数据为准，严禁胡编乱造；若未提供且涉及个人隐私，请引导学生在【个人中心】查看。";

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("stream", true);
            if (aiConfig.getMaxTokens() != null) {
                requestBody.put("max_tokens", aiConfig.getMaxTokens());
            }
            if (StringUtils.hasText(aiConfig.getReasoningEffort())) {
                requestBody.put("reasoning_effort", aiConfig.getReasoningEffort());
            }

            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            String prompt = StringUtils.hasText(personalContext)
                    ? personalContext + "\n用户疑问：" + cleanQuestion
                    : cleanQuestion;
            userMsg.put("content", prompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            String endpoint = aiConfig.getBaseUrl();
            if (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }
            if (!endpoint.endsWith("/chat/completions")) {
                endpoint = endpoint + "/chat/completions";
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("商汤大模型接口返回非200状态: code={}", response.statusCode());
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }
                    if (line.equals("data: [DONE]")) {
                        break;
                    }
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring(5).trim();
                        try {
                            JSONObject obj = JSON.parseObject(jsonStr);
                            JSONArray choices = obj.getJSONArray("choices");
                            if (choices != null && !choices.isEmpty()) {
                                JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                if (delta != null && delta.containsKey("content")) {
                                    String text = delta.getString("content");
                                    if (text != null && !text.isEmpty()) {
                                        sendChunk(emitter, text, false, "SENSENOVA");
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            // 忽略单个 chunk 解析异常
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.error("调用商汤大模型异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 本地智能规则推断与优雅流式生成器 (无 Key 或离线状态下的可靠保障)
     */
    private void streamLocalSynthesizedAnswer(SseEmitter emitter, String question, String personalContext, Long studentId) {
        String answer;
        String q = question.toLowerCase();

        if (StringUtils.hasText(personalContext) && studentId != null) {
            Student student = studentMapper.selectById(studentId);
            int currentPoints = (student != null && student.getCurrentPoints() != null) ? student.getCurrentPoints() : 0;
            int totalPoints = (student != null && student.getTotalPoints() != null) ? student.getTotalPoints() : 0;

            if (q.contains("积分") || q.contains("多少分") || q.contains("余额")) {
                answer = String.format("同学你好！为你核对平台真实数据：\n\n" +
                        "• 你当前**可用积分余额**为：%d 分\n" +
                        "• 历史**累计获得积分**为：%d 分\n\n" +
                        "💡 积分可用于在【积分兑换商城】免费兑换数码、文创等礼品；参加校园活动并在现场扫码打卡可赚取更多积分哦！",
                        currentPoints, totalPoints);
            } else if (q.contains("礼品") || q.contains("订单") || q.contains("提货") || q.contains("兑换")) {
                Long pending = studentGiftMapper.selectCount(new LambdaQueryWrapper<StudentGift>()
                        .eq(StudentGift::getStudentId, studentId)
                        .eq(StudentGift::getStatus, 1)
                        .eq(StudentGift::getDeleted, 0));
                answer = String.format("同学你好！为你查询到：你当前共有 **%d** 笔待发放/待核销的礼品订单。\n\n" +
                        "📦 **领取方式**：请前往【个人中心】点击【我的兑换订单】，出示对应的**提货核销码（Pickup Code）**，前往线下服务台交由辅导员或管理员核销即可领取。",
                        pending);
            } else if (q.contains("活动") || q.contains("打卡") || q.contains("报名") || q.contains("候补")) {
                Long actCount = studentActivityMapper.selectCount(new LambdaQueryWrapper<StudentActivity>()
                        .eq(StudentActivity::getStudentId, studentId)
                        .eq(StudentActivity::getDeleted, 0));
                answer = String.format("同学你好！系统记录显示你已累计参与 **%d** 场校园活动。\n\n" +
                        "📌 **温馨提醒**：\n" +
                        "1. 在【活动打卡大厅】可浏览正在火热报名的活动；\n" +
                        "2. 若活动名额已满，可点击排队进入候补队列，有空额将按序自动递补；\n" +
                        "3. 参加活动时请留意开启手机定位，在现场扫描辅导员出示的动态二维码即可完成打卡！",
                        actCount);
            } else {
                answer = "同学你好！我是校园活动积分助手小智。\n\n" +
                        "你可以咨询我关于**校园活动打卡、动态防伪二维码、GPS范围考勤、积分规则、商城礼品兑换或候补排队机制**等任何问题，我将全力为你解答！";
            }
        } else {
            if (q.contains("积分") || q.contains("规则")) {
                answer = "【积分获取与使用指南】\n\n" +
                        "1. **如何获得积分**：报名参加学术讲座、文体志愿等各类校园活动，到达现场后扫码完成打卡，系统即时发放积分。\n" +
                        "2. **积分有什么用**：可在【积分兑换商城】免费换取精选礼品，兑换后凭订单提货码线下提货。\n" +
                        "3. **注意事项**：请登录学生账号后提问，我可以为你实时查询个人积分余额哦！";
            } else if (q.contains("打卡") || q.contains("定位") || q.contains("二维码")) {
                answer = "【现场扫码打卡指引】\n\n" +
                        "1. 打卡前请确认已成功报名该活动（候补状态需转正后才能打卡）；\n" +
                        "2. 管理员出示的二维码为动态加密二维码，请使用手机即时扫码，避免超时刷新；\n" +
                        "3. 若活动开启了 GPS 考勤核验，请允许手机浏览器获取地理位置，并保持在指定半径范围内。";
            } else {
                answer = "同学你好！我是校园活动小助手。\n\n" +
                        "关于你提到的问题，平台已为你整理了以下常见帮助：\n" +
                        "• 积分说明与商城兑换规则\n" +
                        "• 现场动态二维码与GPS打卡规范\n" +
                        "• 满额候补排队自动递补机制\n\n" +
                        "你可以点击上方推荐问题直接查看官方解答，或联系辅导员老师处理。";
            }
        }

        // 分块打字机流式输出
        int step = 8;
        for (int i = 0; i < answer.length(); i += step) {
            int end = Math.min(i + step, answer.length());
            String chunk = answer.substring(i, end);
            boolean isLast = (end == answer.length());
            sendChunk(emitter, chunk, isLast, "LOCAL_SYNTHESIS");
            sleep(30);
        }
    }

    /**
     * 发送单个 SSE 数据包
     */
    private void sendChunk(SseEmitter emitter, String content, boolean finish, String source) {
        try {
            JSONObject data = new JSONObject();
            data.put("content", content);
            data.put("finish", finish);
            data.put("source", source);
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(data.toJSONString()));
        } catch (Exception e) {
            log.debug("向客户端推送 SSE chunk 失败(可能客户端已断开): {}", e.getMessage());
        }
    }

    /**
     * 发送 SSE 结束事件
     */
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
