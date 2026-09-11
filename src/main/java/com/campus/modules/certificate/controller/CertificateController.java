package com.campus.modules.certificate.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 荣誉证书及真伪核验控制器
 */
@RestController
@RequestMapping({"/certificate", "/admin/certificate"})
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * 公共扫码验真接口
     * GET /certificate/verify/{certificateNo}
     */
    @GetMapping("/verify/{certificateNo}")
    public Result<Certificate> verifyCertificate(@PathVariable String certificateNo) {
        if (certificateNo == null || certificateNo.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "证书编号不能为空");
        }
        Certificate cert = certificateService.getOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertificateNo, certificateNo.trim())
                .eq(Certificate::getDeleted, 0));
        if (cert == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "未查询到该证书档案，可能不存在或已被撤销");
        }
        // 扫码核验次数自增
        cert.setVerifyCount((cert.getVerifyCount() == null ? 0 : cert.getVerifyCount()) + 1);
        certificateService.updateById(cert);
        return Result.success("证书真伪核验通过", cert);
    }

    /**
     * 学生查询我的荣誉证书列表
     * GET /certificate/my-list
     */
    @SaCheckLogin
    @GetMapping("/my-list")
    public Result<List<Certificate>> getMyCertificates() {
        Long studentId = AuthUtil.requireStudentId();
        List<Certificate> list = certificateService.list(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentId, studentId)
                .eq(Certificate::getDeleted, 0)
                .orderByDesc(Certificate::getIssueTime));
        return Result.success(list);
    }

    /**
     * 管理端分页查询证书记录
     * GET /certificate/page
     */
    @SaCheckLogin
    @GetMapping("/page")
    public Result<IPage<Certificate>> pageCertificates(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        AuthUtil.requireAdminId();
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getDeleted, 0)
                .eq(status != null, Certificate::getStatus, status)
                .orderByDesc(Certificate::getCreateTime);

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Certificate::getCertificateNo, kw)
                    .or().like(Certificate::getTitle, kw)
                    .or().like(Certificate::getIssuer, kw));
        }

        return Result.success(certificateService.page(new Page<>(current, pageSize), wrapper));
    }
}
