package com.campus.modules.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理端撤销证书请求。 */
@Data
public class CertificateRevokeDTO {
    @NotBlank(message = "撤销原因不能为空")
    private String revokeReason;
}
