package com.campus.modules.certificate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 荣誉证书与真伪核验大盘聚合指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateMetricsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已颁发证书总量 */
    private Long totalCount;

    /** 正常有效证书数量 (status = 1) */
    private Long validCount;

    /** 已吊销作废证书数量 (status = 2) */
    private Long revokedCount;

    /** 已过期证书数量 (status = 3) */
    private Long expiredCount;

    /** 累计防伪核验次数 */
    private Long verifyTotal;
}
