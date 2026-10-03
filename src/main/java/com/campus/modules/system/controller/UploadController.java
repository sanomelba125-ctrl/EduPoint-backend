package com.campus.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.modules.system.service.QiniuService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 通用文件上传与静态资源控制器
 */
@RestController
@RequestMapping("/common")
public class UploadController {

    private final QiniuService qiniuService;

    public UploadController(QiniuService qiniuService) {
        this.qiniuService = qiniuService;
    }

    /**
     * 图片上传至对象存储 (七牛云或本地磁盘存储)
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

    /**
     * 本地存储模式图片预览/下载接口
     * GET /common/file/{filename}
     */
    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<Resource> getLocalFile(@PathVariable String filename) {
        // 防止路径穿越攻击 (Path Traversal Protection)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException("非法的文件访问路径");
        }
        Path filePath = Paths.get("uploads", "images", filename).normalize();
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        String contentType = "image/png";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            contentType = "image/gif";
        } else if (lower.endsWith(".webp")) {
            contentType = "image/webp";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
