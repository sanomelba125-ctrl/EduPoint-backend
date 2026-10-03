package com.campus.modules.system.service.impl;

import com.campus.common.exception.BusinessException;
import com.campus.config.QiniuProperties;
import com.campus.modules.system.service.QiniuService;
import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 对象存储服务实现类 (支持七牛云与本地磁盘优雅降级)
 * 负责文件上传、格式风控校验、以及私有资源（如电子证书）的按需安全签名
 */
@Slf4j
@Service
public class QiniuServiceImpl implements QiniuService {

    /**
     * 允许上传的图片白名单后缀
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
    );

    private static final Gson GSON = new Gson();

    private final QiniuProperties qiniuProperties;
    private final UploadManager uploadManager;
    private final Auth qiniuAuth;

    public QiniuServiceImpl(QiniuProperties properties,
                            @Autowired(required = false) UploadManager manager,
                            @Autowired(required = false) Auth auth) {
        this.qiniuProperties = properties;
        this.uploadManager = manager;
        this.qiniuAuth = auth;
    }

    /**
     * 上传图片并返回访问 URL
     */
    @Override
    public String uploadImage(MultipartFile file) {
        // 1. 前置安全校验
        validateFile(file);

        // 2. 本地存储模式降级处理 (未启用七牛云或客户端为 null)
        if (!qiniuProperties.isQiniuEnabled() || uploadManager == null || qiniuAuth == null) {
            return saveToLocal(file);
        }

        // 3. 生成基于日期分级的唯一文件存储路径 (key)
        String suffix = getFileSuffix(file.getOriginalFilename());
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileKey = String.format("images/%s/%s%s", datePath, UUID.randomUUID().toString().replace("-", ""), suffix);

        try {
            // 4. 签发上传凭证并推送至七牛云存储
            String uploadToken = qiniuAuth.uploadToken(qiniuProperties.getBucket());
            Response response = uploadManager.put(file.getBytes(), fileKey, uploadToken);

            if (!response.isOK()) {
                log.error("七牛云上传失败，状态码: {}, 错误信息: {}", response.statusCode, response.error);
                throw new BusinessException("图片上传至云存储失败: " + response.error);
            }

            // 5. 解析七牛云响应结果
            DefaultPutRet putRet = GSON.fromJson(response.bodyString(), DefaultPutRet.class);
            String rawUrl = buildPublicUrl(putRet.key);

            // 6. 兼容当前私有空间：若当前存储桶启用了私有防盗链，签发长期可访问地址避免 401
            try {
                return qiniuAuth.privateDownloadUrl(rawUrl, 315360000L);
            } catch (Exception e) {
                log.warn("生成私有下载链接失败，直接回落为原始地址: {}", rawUrl);
                return rawUrl;
            }

        } catch (QiniuException e) {
            log.error("七牛云 SDK 交互异常: ", e);
            throw new BusinessException("七牛云存储网络通信异常，请稍后重试");
        } catch (IOException e) {
            log.error("读取上传文件二进制流失败: ", e);
            throw new BusinessException("读取上传文件内容失败: " + e.getMessage());
        }
    }

    /**
     * 保存图片至本地磁盘目录 (uploads/images)
     */
    private String saveToLocal(MultipartFile file) {
        try {
            Path uploadDir = Paths.get("uploads", "images");
            Files.createDirectories(uploadDir);
            String suffix = getFileSuffix(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + suffix;
            Path targetPath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            String accessUrl = "/api/common/file/" + filename;
            log.info("[OSS] 本地磁盘存储模式：图片上传成功 path={}, url={}", targetPath.toAbsolutePath(), accessUrl);
            return accessUrl;
        } catch (IOException e) {
            log.error("[OSS] 本地保存图片异常", e);
            throw new BusinessException("本地保存图片失败: " + e.getMessage());
        }
    }

    /**
     * 为私有资源（如后续的电子荣誉证书、敏感文件）按需生成短期高安全访问签名
     *
     * @param targetUrlOrKey 原始文件 URL 或存储 Key
     * @param expireSeconds  有效秒数（例如 300 表示 5 分钟后自动作废）
     * @return 携带签名参数的时效性 URL
     */
    @Override
    public String generatePresignedUrl(String targetUrlOrKey, long expireSeconds) {
        if (targetUrlOrKey == null || targetUrlOrKey.isBlank()) {
            return "";
        }

        String fullUrl = targetUrlOrKey.startsWith("http://") || targetUrlOrKey.startsWith("https://")
                ? targetUrlOrKey
                : buildPublicUrl(targetUrlOrKey);

        if (!qiniuProperties.isQiniuEnabled() || qiniuAuth == null) {
            return fullUrl;
        }

        try {
            return qiniuAuth.privateDownloadUrl(fullUrl, expireSeconds);
        } catch (Exception e) {
            log.error("生成私有签名下载链接失败, url: {}", fullUrl, e);
            return fullUrl;
        }
    }

    /**
     * 文件有效性与安全性前置校验
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的文件不能为空");
        }
        if (qiniuProperties.isQiniuEnabled() && !qiniuProperties.isConfigured()) {
            throw new BusinessException("七牛云参数未配置，请联系系统管理员");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BusinessException("上传的文件名不合法");
        }

        String suffix = getFileSuffix(originalName);
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            throw new BusinessException("仅支持上传 " + ALLOWED_EXTENSIONS + " 格式的图片");
        }
    }

    /**
     * 提取小写后缀名
     */
    private String getFileSuffix(String filename) {
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    /**
     * 拼接基础无参数的完整访问地址
     */
    private String buildPublicUrl(String fileKey) {
        String domain = qiniuProperties.getDomainUrl();
        if (domain == null || domain.isBlank()) {
            return fileKey;
        }

        domain = domain.trim();
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "http://" + domain;
        }
        if (!domain.endsWith("/")) {
            domain += "/";
        }

        return domain + fileKey;
    }
}
