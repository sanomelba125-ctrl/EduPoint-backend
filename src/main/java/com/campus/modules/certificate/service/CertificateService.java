package com.campus.modules.certificate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMetricsVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;

/**
 * Certificate 服务接口
 * 负责业务逻辑定义
 */
public interface CertificateService extends IService<Certificate> {
    java.util.List<CertificateMyVO> getMyList();

    CertificateVerifyVO verify(String certificateNo);

    IPage<CertificateAdminVO> getAdminPage(Integer page, Integer size, String certificateNo,
                                            String studentName, String studentNo, String activityTitle, Integer status);

    void revoke(Long id, String revokeReason);

    CertificateMetricsVO getMetrics();
}
