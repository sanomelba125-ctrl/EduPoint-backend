package com.campus.modules.activity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 学生活动参与/报名状态枚举
 * 对应表 student_activity.status 字段
 */
@Getter
public enum StudentActivityStatus implements IEnum<Integer> {

    REGISTERED(1, "已报名(待打卡)"),
    COMPLETED(2, "已完成(打卡成功)"),
    CANCELLED(3, "已取消/已失效");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    StudentActivityStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static StudentActivityStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (StudentActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
