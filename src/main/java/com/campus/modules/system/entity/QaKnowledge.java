package com.campus.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI客服与平台问答知识库实体
 * 对应需�? 3.15 AI 客服 / 知识内容与运行配�? */
@Data
@TableName("qa_knowledge")
public class QaKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识分类: RULE-平台规则, ACTIVITY-活动打卡, GIFT-礼品兑换, SCORE-积分说明, OTHER-其他 */
    private String category;

    /** 问题标题/意图匹配关键�?*/
    private String question;

    /** 标准官方解答正文 */
    private String answer;

    /** 检索匹配关键词(逗号分隔) */
    private String keywords;

    /** 启用状�? 0-禁用 1-启用 */
    private Integer status;

    /** 排序权重(越小越靠�? */
    private Integer sortOrder;

    /** 被咨询查阅次�?*/
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
