package com.campus.modules.ai.dto;

import lombok.Data;

/**
 * 知识库后台分页筛选参数
 */
@Data
public class QaKnowledgePageQuery {

    private Integer page = 1;

    private Integer size = 10;

    /** 分类筛选 */
    private String category;

    /** 关键词模糊匹配 (问题标题或关键词) */
    private String keyword;

    /** 状态: 0-禁用 1-启用 */
    private Integer status;
}
