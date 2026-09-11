package com.campus.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.Result;
import com.campus.modules.system.service.QiniuService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传控制器
 */
@RestController
@RequestMapping("/common")
public class UploadController {

    private final QiniuService qiniuService;

    public UploadController(QiniuService qiniuService) {
        this.qiniuService = qiniuService;
    }

    /**
     * 图片上传至七牛云 (返回外链 URL)
     * 【安全加固】：增加 @SaCheckLogin，只有登录成功的管理员或学生才允许上传，防止接口被公网脚本刷库
     */
    @SaCheckLogin
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        // 双重校验当前会话登录态
        StpUtil.checkLogin();

        String imageUrl = qiniuService.uploadImage(file);
        return Result.success("图片上传成功", imageUrl);
    }
}
