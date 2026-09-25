package com.campus.modules.ai.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.ai.entity.AiChatMessage;
import com.campus.modules.ai.mapper.AiChatMessageMapper;
import com.campus.modules.ai.service.AiMessageService;
import com.campus.modules.ai.vo.AiChatMessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 对话消息持久化与历史查询领域服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageServiceImpl implements AiMessageService {

    private final AiChatMessageMapper aiChatMessageMapper;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public List<AiChatMessageVO> getChatHistory(Long studentId, String conversationId, int limit) {
        int queryLimit = (limit <= 0 || limit > 100) ? 50 : limit;

        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getDeleted, 0);

        if (studentId != null) {
            wrapper.eq(AiChatMessage::getStudentId, studentId);
        } else if (StringUtils.hasText(conversationId)) {
            wrapper.eq(AiChatMessage::getConversationId, conversationId);
        } else {
            // 未登录且未传会话ID，为防信息泄露返回空列表
            return Collections.emptyList();
        }

        if (StringUtils.hasText(conversationId)) {
            wrapper.eq(AiChatMessage::getConversationId, conversationId);
        }

        // 倒序取最新的 queryLimit 条
        wrapper.orderByDesc(AiChatMessage::getCreateTime)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + queryLimit);

        List<AiChatMessage> list = aiChatMessageMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // 翻转为按时间正序排列（旧消息在顶部，新消息在底部）
        Collections.reverse(list);

        List<AiChatMessageVO> voList = new ArrayList<>(list.size());
        for (AiChatMessage msg : list) {
            JSONObject toolObj = null;
            if (StringUtils.hasText(msg.getToolCallJson())) {
                try {
                    toolObj = JSON.parseObject(msg.getToolCallJson());
                } catch (Exception e) {
                    log.warn("解析历史消息 toolCallJson 异常: id={}, error={}", msg.getId(), e.getMessage());
                }
            }

            String timeStr = msg.getCreateTime() != null ? msg.getCreateTime().format(TIME_FORMATTER) : "";

            voList.add(AiChatMessageVO.builder()
                    .id(msg.getId())
                    .studentId(msg.getStudentId())
                    .conversationId(msg.getConversationId())
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .source(msg.getSource())
                    .toolCall(toolObj)
                    .createTime(msg.getCreateTime())
                    .time(timeStr)
                    .build());
        }

        return voList;
    }

    @Override
    public void clearChatHistory(Long studentId, String conversationId) {
        if (studentId == null && !StringUtils.hasText(conversationId)) {
            return;
        }

        LambdaQueryWrapper<AiChatMessage> wrapper = new LambdaQueryWrapper<>();

        if (studentId != null) {
            wrapper.eq(AiChatMessage::getStudentId, studentId);
        }
        if (StringUtils.hasText(conversationId)) {
            wrapper.eq(AiChatMessage::getConversationId, conversationId);
        }

        int affected = aiChatMessageMapper.delete(wrapper);
        log.info("【AI 对话】已清空历史消息: studentId={}, conversationId={}, affected={}", studentId, conversationId, affected);
    }

    @Override
    public void saveMessage(Long studentId, String conversationId, String role, String content, String source, String toolCallJson) {
        if (!StringUtils.hasText(content) && !StringUtils.hasText(toolCallJson)) {
            return;
        }
        try {
            AiChatMessage message = AiChatMessage.builder()
                    .studentId(studentId)
                    .conversationId(StringUtils.hasText(conversationId) ? conversationId : "default")
                    .role(role)
                    .content(content != null ? content : "")
                    .source(source)
                    .toolCallJson(toolCallJson)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .deleted(0)
                    .build();
            aiChatMessageMapper.insert(message);
            log.debug("【AI 对话消息持久化】成功落库: [ID:{}] role={}, studentId={}", message.getId(), role, studentId);
        } catch (Exception e) {
            log.error("【AI 对话消息持久化】落库异常: studentId={}, role={}, error={}", studentId, role, e.getMessage());
        }
    }
}
