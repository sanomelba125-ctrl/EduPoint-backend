package com.campus.modules.certificate.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.auth.AuthUtil;
import com.campus.common.result.Result;
import com.campus.modules.certificate.dto.*;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 荣誉证书及真伪核验控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping({"/certificate", "/admin/certificate"})
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * 8.1 学生查询自己的电子证书列表。
     * GET /certificate/my-list
     */
    @SaCheckLogin
    @GetMapping("/my-list")
    public Result<List<CertificateMyVO>> myList() {
        return Result.success(certificateService.getMyList());
    }

    /**
     * 8.2 公开扫码验真接口，完全公开无需登录（姓名自动脱敏保护）
     * GET /certificate/verify/{certificateNo}
     */
    @GetMapping("/verify/{certificateNo}")
    public Result<CertificateVerifyVO> verify(@PathVariable String certificateNo) {
        return Result.success(certificateService.verify(certificateNo));
    }

    /**
     * 8.3 管理端分页多条件查询证书台账（连表带出学生姓名、学号、活动标题等）
     * GET /certificate/list
     */
    @GetMapping("/list")
    public Result<IPage<CertificateAdminVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String certificateNo,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String activityTitle,
            @RequestParam(required = false) Integer status) {
        return Result.success(certificateService.getAdminPage(page, size, certificateNo, studentName, studentNo, activityTitle, status));
    }

    /**
     * 兼容 dev 分支的单表分页查询
     * GET /certificate/page
     */
    @GetMapping("/page")
    public Result<IPage<Certificate>> pageCertificates(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        AuthUtil.requireAdminId();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);

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

    /**
     * 8.4 管理端撤销有效证书。
     * PUT /certificate/{id}/revoke
     */
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id, @Valid @RequestBody CertificateRevokeDTO dto) {
        certificateService.revoke(id, dto.getRevokeReason());
        return Result.success("证书已撤销", null);
    }
}
