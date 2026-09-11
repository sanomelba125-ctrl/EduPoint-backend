package com.campus.modules.ai.dto;

import lombok.Data;

/**
 * 知识库条目增改 DTO
 */
@Data
public class QaKnowledgeDTO {

    private Long id;

    /** 分类: RULE-平台规则, ACTIVITY-活动打卡, GIFT-礼品兑换, SCORE-积分说明, OTHER-其他 */
    private String category;

    /** 问题标题/意图 */
    private String question;

    /** 权威官方解答 */
    private String answer;

    /** 检索关键词(英文逗号分隔) */
    private String keywords;

    /** 排序权重(越小越靠前) */
    private Integer sortOrder;

    /** 启用状态: 0-禁用 1-启用 */
    private Integer status;
}
