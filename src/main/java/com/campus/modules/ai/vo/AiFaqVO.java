package com.campus.modules.ai.vo;

import lombok.Data;

/**
 * 推荐问答视图对象 (用于前置快捷气泡展示)
 */
@Data
public class AiFaqVO {

    private Long id;

    /** 分类: RULE/ACTIVITY/GIFT/SCORE/OTHER */
    private String category;

    /** 问题标题 */
    private String question;

    /** 核心检索关键词 */
    private String keywords;
}
