package com.campus.modules.certificate.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;
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
        return baseMapper.selectMyList(studentId);
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
        if (result.getStatus() == null || result.getStatus() < 1 || result.getStatus() > 3) {
            result.setStatus(3);
        }
        // 验真接口保持公开，但只返回脱敏姓名；撤销/过期证书禁止下载文件。
        if (result.getStatus() != null && result.getStatus() == 1) {
            result.setStatusText("有效");
        } else if (result.getStatus() != null && result.getStatus() == 2) {
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
        return baseMapper.selectAdminPage(new Page<>(current, pageSize), trim(certificateNo), trim(studentName),
                trim(studentNo), trim(activityTitle), status);
    }

    @Override
    public void revoke(Long id, String revokeReason) {
        Long adminId = requireAdminId();
        if (id == null || revokeReason == null || revokeReason.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "证书ID和撤销原因不能为空");
        }
        Certificate certificate = getById(id);
        if (certificate == null) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "证书不存在");
        if (!Integer.valueOf(1).equals(certificate.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅有效证书可以撤销");
        }
        certificate.setStatus(2);
        certificate.setRevokeReason(revokeReason.trim());
        certificate.setRevokedBy(adminId);
        certificate.setRevokedTime(LocalDateTime.now());
        certificate.setFileUrl(null);
        updateById(certificate);
    }

    private Long requireStudentId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith("student:")) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅学生可查询个人证书");
        }
        try {
            return Long.parseLong(loginId.substring("student:".length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录状态无效");
        }
    }

    private Long requireAdminId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith("admin:")) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问管理员证书接口");
        }
        try {
            return Long.parseLong(loginId.substring("admin:".length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录状态无效");
        }
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        if (name.length() == 1) return "*";
        return name.charAt(0) + "*";
    }
}
