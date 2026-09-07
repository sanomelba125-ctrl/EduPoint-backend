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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 七牛云对象存储实现类
 */
@Service
public class QiniuServiceImpl implements QiniuService {

    private static final Logger log = LoggerFactory.getLogger(QiniuServiceImpl.class);

    private final QiniuProperties qiniuProperties;
    private final UploadManager uploadManager;
    private final Auth qiniuAuth;

    public QiniuServiceImpl(QiniuProperties qiniuProperties, UploadManager uploadManager, Auth qiniuAuth) {
        this.qiniuProperties = qiniuProperties;
        this.uploadManager = uploadManager;
        this.qiniuAuth = qiniuAuth;
    }

    /**
     * 允许上传的图片扩展名白名�?     */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"
    );

    @Override
    public String uploadImage(MultipartFile file) {
        // 1. 空文件校�?        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的文件不能为�?);
        }

        // 2. 检查七牛云密钥是否已在配置文件中填�?        if (!qiniuProperties.isConfigured()) {
            throw new BusinessException("七牛云参数未配置！请先在 application-dev.yml 中填写你�?access-key、secret-key �?bucket 空间�?);
        }

        // 3. 文件名与格式校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new BusinessException("上传的文件名不合�?);
        }

        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(suffix)) {
            throw new BusinessException("不支持的文件格式，仅允许上传常见图片 (.jpg, .jpeg, .png, .webp, .gif)");
        }

        // 4. 按日期分层目录并生成全局唯一文件名：images/20260907/uuid.png
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String key = "images/" + datePath + "/" + uniqueFileName;

        try {
            // 5. 生成七牛云上传凭�?            String upToken = qiniuAuth.uploadToken(qiniuProperties.getBucket());

            // 6. 执行流式上传
            Response response = uploadManager.put(file.getBytes(), key, upToken);

            if (!response.isOK()) {
                log.error("七牛云上传返回错误状态码: {}, 错误详情: {}", response.statusCode, response.error);
                throw new BusinessException("图片上传至七牛云失败: " + response.error);
            }

            // 7. 解析七牛云返回结�?            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
            String uploadedKey = putRet.key;

            // 8. 拼接并返回完整的外链访问 URL
            String domain = qiniuProperties.getDomainUrl();
            if (domain == null || domain.isBlank()) {
                return uploadedKey;
            }

            domain = domain.trim();
            if (!domain.endsWith("/")) {
                domain += "/";
            }
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "http://" + domain;
            }

            String fullUrl = domain + uploadedKey;
            log.info("图片成功上传至七牛云: {}", fullUrl);
            return fullUrl;

        } catch (QiniuException ex) {
            Response r = ex.response;
            log.error("七牛�?SDK 上传异常: {}", r != null ? r.toString() : ex.getMessage(), ex);
            throw new BusinessException("七牛云存储异�? " + (r != null ? r.error : ex.getMessage()));
        } catch (IOException e) {
            log.error("读取上传图片字节流失�?, e);
            throw new BusinessException("读取上传文件内容失败: " + e.getMessage());
        }
    }
}
