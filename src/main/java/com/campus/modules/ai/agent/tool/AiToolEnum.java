package com.campus.modules.ai.agent.tool;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI Function Calling 智能工具枚举定义
 * 对应《智能交互与凭证中心实施规划》5 大只读核心工具
 */
@Getter
@RequiredArgsConstructor
public enum AiToolEnum {

    QUERY_POINTS("query_my_points", "查询当前登录学生本人的实时可用积分余额、累计总积分及最新收支流水记录"),
    QUERY_ORDERS("query_my_orders", "查询当前登录学生本人的商城礼品兑换订单列表与提货核销码状态"),
    QUERY_ACTIVITIES("query_my_activities", "查询学生本人已报名、已打卡或正在候补排队的校园活动列表"),
    QUERY_CERTIFICATES("query_my_certificates", "查询学生本人获得的荣誉结项证书及唯一防伪核验编号"),
    SEARCH_KNOWLEDGE("search_platform_knowledge", "按关键词从学校官方知识库中检索打卡、积分、选拔与评奖规则解答");

    private final String functionName;
    private final String description;
}
