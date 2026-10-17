package com.campus.modules.certificate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 学生端电子证书展示对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertificateMyVO {
    private Long id;
    private String certificateNo;
    private Long activityId;
    private String activityTitle;
    private String title;
    private String issuer;
    private LocalDateTime issueTime;
    private String fileUrl;
    private Integer status;
}
