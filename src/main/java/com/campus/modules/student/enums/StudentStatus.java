package com.campus.modules.student.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 学生账户状态枚举
 * 对应表 student.status 字段
 */
@Getter
public enum StudentStatus implements IEnum<Integer> {

    DISABLED(0, "已禁用/锁定"),
    NORMAL(1, "正常启用");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    StudentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static StudentStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (StudentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
