package com.campus.modules.ai.service;

import com.campus.modules.ai.vo.AiChatMessageVO;

import java.util.List;

/**
 * AI 对话消息持久化与历史查询领域服务接口
 */
public interface AiMessageService {

    /**
     * 查询对话历史列表 (按时间正序排列)
     *
     * @param studentId      学生ID
     * @param conversationId 会话分组ID
     * @param limit          最大条数
     * @return 历史消息列表
     */
    List<AiChatMessageVO> getChatHistory(Long studentId, String conversationId, int limit);

    /**
     * 清空指定会话或指定学生的历史消息
     *
     * @param studentId      学生ID
     * @param conversationId 会话分组ID
     */
    void clearChatHistory(Long studentId, String conversationId);

    /**
     * 持久化单条对话消息 (用户提问或助理回答)
     *
     * @param studentId      学生ID
     * @param conversationId 会话分组ID
     * @param role           角色 (user / assistant / system)
     * @param content        消息正文
     * @param source         应答引擎来源
     * @param toolCallJson   工具调用轨迹与事实数据 JSON
     */
    void saveMessage(Long studentId, String conversationId, String role, String content, String source, String toolCallJson);
}
