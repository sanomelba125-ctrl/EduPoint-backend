package com.campus.modules.mall.entity;

/**
 * 积分流水业务类型。
 * 这里的 code 是数据库 student_point_record.type 的取值，不可随意调整。
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
