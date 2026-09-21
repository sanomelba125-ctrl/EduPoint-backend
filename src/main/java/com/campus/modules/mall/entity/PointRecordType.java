package com.campus.modules.mall.entity;

/**
 * 积分流水业务类型。
 * 这里的 code 是数据库 student_point_record.type 的取值，不可随意调整。
 *
 * <p>四种类型与业务路径一一对应，其中只有 1 和 3（正向）会同时影响累计积分：
 * <ul>
 *   <li>{@link #ACTIVITY_CHECKIN}(1) 活动打卡奖励 —— 可用、累计同时增加，change 为正；</li>
 *   <li>{@link #GIFT_EXCHANGE}(2) 商城兑换消费 —— 只扣可用，change 为负；</li>
 *   <li>{@link #ADMIN_ADJUST}(3) 系统人工调账 —— change 可正可负，扣分时不动累计；</li>
 *   <li>{@link #ORDER_REFUND}(4) 订单取消返还 —— 只加可用，change 为正（退的是自己的支出，不算新增收入）。</li>
 * </ul>
 *
 * <p>另需注意：学生端「全部收入 / 全部支出」两个筛选项用的是伪类型 100 / 200，
 * 它们并不在本枚举内，而是按 {@code change_points} 的正负号过滤，
 * 详见 {@code StudentScoreServiceImpl#getMyPointRecords}。
 *
 * <p>写流水时应使用本枚举常量而不是裸数字（如 {@code 4}），否则会失去「谁在写流水」的可搜索性。
 * 当前 4 处写入点仍在传字面量，属待收敛的技术债。
 */
public enum PointRecordType {

    ACTIVITY_CHECKIN(1, "活动打卡奖励"),
    GIFT_EXCHANGE(2, "商城兑换消费"),
    ADMIN_ADJUST(3, "系统人工调账"),
    ORDER_REFUND(4, "订单取消返还"),
    UNKNOWN(0, "其他流水变动");

    private final int code;
    private final String description;

    PointRecordType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static PointRecordType of(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (PointRecordType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
