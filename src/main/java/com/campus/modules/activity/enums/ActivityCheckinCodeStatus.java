package com.campus.modules.activity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 活动动态打卡二维码状态枚举
 * 对应表 activity_checkin_code.status 字段
 */
@Getter
public enum ActivityCheckinCodeStatus implements IEnum<Integer> {

    UNUSED(0, "有效待核销"),
    USED(1, "已核销/已使用"),
    EXPIRED(2, "已失效/已过期");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    ActivityCheckinCodeStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static ActivityCheckinCodeStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ActivityCheckinCodeStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
