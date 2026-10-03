package com.campus.common.exception;

import com.campus.common.result.ResultCode;
import lombok.Getter;

public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 携带领域状态码与动态补充业务说明
     *
     * @param resultCode 领域状态码
     * @param detail     动态详情或扩展说明
     */
    public BusinessException(ResultCode resultCode, String detail) {
        super((detail == null || detail.isBlank())
                ? resultCode.getMessage()
                : (detail.startsWith(resultCode.getMessage()) ? detail : resultCode.getMessage() + "：" + detail));
        this.code = resultCode.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
