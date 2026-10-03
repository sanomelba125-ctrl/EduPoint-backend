package com.campus.modules.certificate.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.annotation.RateLimit;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.campus.common.result.Result;
import com.campus.modules.certificate.dto.*;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import com.campus.modules.certificate.service.CertificateRenderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 荣誉证书及真伪核验控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping({"/certificate", "/admin/certificate"})
public class CertificateController {

    private final CertificateService certificateService;
    private final CertificateRenderService certificateRenderService;

    @Autowired
    public CertificateController(CertificateService certificateService,
                                 CertificateRenderService certificateRenderService) {
        this.certificateService = java.util.Objects.requireNonNull(certificateService, "certificateService must not be null");
        this.certificateRenderService = java.util.Objects.requireNonNull(certificateRenderService, "certificateRenderService must not be null");
    }

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
     * 防刷加固：单 IP 每秒最多 10 次请求，超限返回 429 TOO_MANY_REQUESTS
     * GET /certificate/verify/{certificateNo}
     */
    @RateLimit(
            key = "rate_limit:certificate:verify",
            count = 10,
            windowMs = 1000L,
            limitType = RateLimit.LimitType.IP,
            message = "请求过于频繁，证书验真接口已被限流，请稍后重试！"
    )
    @GetMapping("/verify/{certificateNo}")
    public Result<CertificateVerifyVO> verify(@PathVariable String certificateNo) {
        return Result.success(certificateService.verify(certificateNo));
    }

    /**
     * 8.3 管理端分页多条件查询证书台账（连表带出学生姓名、学号、活动标题等）
     * GET /certificate/list
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
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
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
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
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id, @Valid @RequestBody CertificateRevokeDTO dto) {
        certificateService.revoke(id, dto.getRevokeReason());
        return Result.success("证书已撤销", null);
    }

    /**
     * 8.5 管理端荣誉证书及防伪核验指标概览。
     * GET /certificate/metrics 或 /admin/certificate/metrics
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @GetMapping({"/metrics", "/admin/metrics"})
    public Result<CertificateMetricsVO> metrics() {
        return Result.success(certificateService.getMetrics());
    }

    /**
     * 8.6 电子荣誉证书高清图片预览与下载流（P2-12：支持扫码即看、直链预览与长按保存）
     * GET /certificate/file/{certificateNo}.png 或 GET /certificate/{certificateNo}/image
     */
    @GetMapping(value = {"/file/{certificateNo}.png", "/file/{certificateNo}", "/{certificateNo}/image"}, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCertificateImage(@PathVariable("certificateNo") String certificateNo) {
        if (certificateNo == null || certificateNo.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String cleanNo = certificateNo.trim();
        if (cleanNo.toLowerCase().endsWith(".png")) {
            cleanNo = cleanNo.substring(0, cleanNo.length() - 4);
        }
        byte[] bytes = certificateRenderService.renderToPngBytesByNo(cleanNo);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(bytes);
    }
}
