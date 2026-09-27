package com.campus.modules.mall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 礼品兑换订单流转状态枚举
 * 对应表 student_gift.status 字段
 */
@Getter
public enum OrderStatus implements IEnum<Integer> {

    PENDING(1, "待核销/待发放"),
    DELIVERED(2, "已核销/已送达"),
    CANCELLED(3, "已取消");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
