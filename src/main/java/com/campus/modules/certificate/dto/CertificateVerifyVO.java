package com.campus.modules.certificate.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** 公开验真结果，确保不泄露学生敏感信息。 */
@Data
public class CertificateVerifyVO {
    @JsonIgnore
    private Long id;
    private String certificateNo;
    private String studentName;
    private String activityTitle;
    private String title;
    private String issuer;
    private LocalDateTime issueTime;
    private String fileUrl;
    private Integer status;
    private String statusText;
    private String revokeReason;
}
