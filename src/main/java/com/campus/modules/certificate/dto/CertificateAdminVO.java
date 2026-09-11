package com.campus.modules.certificate.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端证书台账展示对象。 */
@Data
public class CertificateAdminVO {
    private Long id;
    private String certificateNo;
    private Long studentId;
    private String studentName;
    private String studentNo;
    private Long activityId;
    private String activityTitle;
    private String title;
    private String issuer;
    private LocalDateTime issueTime;
    private String fileUrl;
    private Integer status;
    private String revokeReason;
    private Long revokedBy;
    private LocalDateTime revokedTime;
    private Integer verifyCount;
}
