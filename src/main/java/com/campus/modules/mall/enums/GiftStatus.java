package com.campus.modules.mall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 积分商城礼品状态枚举
 * 对应表 gift.status 字段
 */
@Getter
public enum GiftStatus implements IEnum<Integer> {

    OFF_SHELF(0, "已下架停用"),
    ON_SHELF(1, "上架兑换中");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    GiftStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static GiftStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (GiftStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
