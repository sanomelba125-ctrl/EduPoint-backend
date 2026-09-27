package com.campus.modules.certificate.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMetricsVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.enums.CertificateStatus;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.certificate.service.CertificateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Certificate 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class CertificateServiceImpl extends ServiceImpl<CertificateMapper, Certificate> implements CertificateService {
    @Override
    public List<CertificateMyVO> getMyList() {
        Long studentId = requireStudentId();
        List<CertificateMyVO> list = baseMapper.selectMyList(studentId);
        if (list != null) {
            for (CertificateMyVO vo : list) {
                if (vo.getFileUrl() == null || vo.getFileUrl().isBlank()) {
                    vo.setFileUrl("/api/certificate/file/" + vo.getCertificateNo() + ".png");
                }
            }
        }
        return list;
    }

    @Override
    public CertificateVerifyVO verify(String certificateNo) {
        if (certificateNo == null || certificateNo.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "证书编号不能为空");
        }
        CertificateVerifyVO result = baseMapper.selectVerifyByNo(certificateNo.trim());
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "证书不存在");
        }
        // 仅将有效证书的文件地址暴露给公众；撤销/过期证书不允许下载。
        if (result.getStatus() == null || result.getStatus() < CertificateStatus.VALID.getCode() || result.getStatus() > CertificateStatus.EXPIRED.getCode()) {
            result.setStatus(CertificateStatus.EXPIRED.getCode());
        }
        // 验真接口保持公开，但只返回脱敏姓名；撤销/过期证书禁止下载文件。
        if (result.getStatus() != null && result.getStatus() == CertificateStatus.VALID.getCode()) {
            result.setStatusText("有效");
            if (result.getFileUrl() == null || result.getFileUrl().isBlank()) {
                result.setFileUrl("/api/certificate/file/" + result.getCertificateNo() + ".png");
            }
        } else if (result.getStatus() != null && result.getStatus() == CertificateStatus.REVOKED.getCode()) {
            result.setStatusText("已作废撤销");
            result.setFileUrl(null);
        } else {
            result.setStatusText("已过期");
            result.setFileUrl(null);
        }
        result.setStudentName(maskName(result.getStudentName()));
        if (result.getId() != null) baseMapper.incrementVerifyCount(result.getId());
        return result;
    }

    @Override
    public IPage<CertificateAdminVO> getAdminPage(Integer page, Integer size, String certificateNo,
                                                   String studentName, String studentNo, String activityTitle, Integer status) {
        requireAdminId();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);
        IPage<CertificateAdminVO> voPage = baseMapper.selectAdminPage(new Page<>(current, pageSize), trim(certificateNo), trim(studentName),
                trim(studentNo), trim(activityTitle), status);
        if (voPage != null && voPage.getRecords() != null) {
            for (CertificateAdminVO vo : voPage.getRecords()) {
                if (vo.getFileUrl() == null || vo.getFileUrl().isBlank()) {
                    vo.setFileUrl("/api/certificate/file/" + vo.getCertificateNo() + ".png");
                }
            }
        }
        return voPage;
    }

    @Override
    public void revoke(Long id, String revokeReason) {
        Long adminId = requireAdminId();
        if (id == null || revokeReason == null || revokeReason.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "证书ID和撤销原因不能为空");
        }
        Certificate certificate = getById(id);
        if (certificate == null) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "证书不存在");
        if (!Integer.valueOf(CertificateStatus.VALID.getCode()).equals(certificate.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅有效证书可以撤销");
        }
        certificate.setStatus(CertificateStatus.REVOKED);
        certificate.setRevokeReason(revokeReason.trim());
        certificate.setRevokedBy(adminId);
        certificate.setRevokedTime(LocalDateTime.now());
        certificate.setFileUrl(null);
        updateById(certificate);
    }

    @Override
    public CertificateMetricsVO getMetrics() {
        requireAdminId();
        CertificateMetricsVO vo = baseMapper.selectMetrics();
        return vo != null ? vo : CertificateMetricsVO.builder()
                .totalCount(0L)
                .validCount(0L)
                .revokedCount(0L)
                .expiredCount(0L)
                .verifyTotal(0L)
                .build();
    }

    private Long requireStudentId() {
        return com.campus.common.auth.AuthUtil.requireStudentId();
    }

    private Long requireAdminId() {
        return com.campus.common.auth.AuthUtil.requireAdminId();
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        if (name.length() == 1) return "*";
        return name.charAt(0) + "*";
    }
}
