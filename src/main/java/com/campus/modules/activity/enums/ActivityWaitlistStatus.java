package com.campus.modules.activity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 活动报名候补排队状态枚举
 * 对应表 activity_waitlist.status 字段
 */
@Getter
public enum ActivityWaitlistStatus implements IEnum<Integer> {

    WAITING(1, "候补中"),
    PROMOTED(2, "已递补转正"),
    CANCELLED(3, "主动取消"),
    EXPIRED(4, "活动结束已失效");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    ActivityWaitlistStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static ActivityWaitlistStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ActivityWaitlistStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
