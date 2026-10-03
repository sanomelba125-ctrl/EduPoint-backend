package com.campus.modules.admin.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 管理域系统用户账号状态枚举
 * 对应表 sys_user.status 字段
 */
@Getter
public enum SysUserStatus implements IEnum<Integer> {

    DISABLED(0, "已禁用/锁定"),
    ENABLED(1, "正常启用");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    SysUserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static SysUserStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (SysUserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
