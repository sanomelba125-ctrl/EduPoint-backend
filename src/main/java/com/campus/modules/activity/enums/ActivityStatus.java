package com.campus.modules.activity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 校园活动生命周期状态枚举
 * 对应表 activity.status 字段
 */
@Getter
public enum ActivityStatus implements IEnum<Integer> {

    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    ENDED(2, "已结束"),
    CLOSED(3, "已关闭/归档");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    ActivityStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static ActivityStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
