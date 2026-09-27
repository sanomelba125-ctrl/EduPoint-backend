package com.campus.modules.certificate.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 电子荣誉证书状态枚举
 * 对应表 certificate.status 字段
 */
@Getter
public enum CertificateStatus implements IEnum<Integer> {

    VALID(1, "有效"),
    REVOKED(2, "已撤销"),
    EXPIRED(3, "已过期");

    @EnumValue
    @JsonValue
    private final int code;
    private final String description;

    CertificateStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public Integer getValue() {
        return this.code;
    }

    public static CertificateStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CertificateStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
