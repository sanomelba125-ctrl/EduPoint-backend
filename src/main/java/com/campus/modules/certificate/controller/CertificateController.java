package com.campus.modules.certificate.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.result.Result;
import com.campus.modules.certificate.dto.*;
import com.campus.modules.certificate.service.CertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Certificate 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/certificate")
@RequiredArgsConstructor
public class CertificateController {
    private final CertificateService service;

    /** 学生查询自己的电子证书。 */
    @GetMapping("/my-list")
    public Result<java.util.List<CertificateMyVO>> myList() {
        return Result.success(service.getMyList());
    }

    /** 公开证书验真，不需要登录。 */
    @GetMapping("/verify/{certificateNo}")
    public Result<CertificateVerifyVO> verify(@PathVariable String certificateNo) {
        return Result.success(service.verify(certificateNo));
    }

    /** 管理端分页查询证书台账。 */
    @GetMapping("/list")
    public Result<IPage<CertificateAdminVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String certificateNo,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String activityTitle,
            @RequestParam(required = false) Integer status) {
        return Result.success(service.getAdminPage(page, size, certificateNo, studentName, studentNo, activityTitle, status));
    }

    /** 管理端撤销有效证书。 */
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id, @Valid @RequestBody CertificateRevokeDTO dto) {
        service.revoke(id, dto.getRevokeReason());
        return Result.success("证书已撤销", null);
    }
}
