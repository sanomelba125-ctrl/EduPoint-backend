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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class QiniuServiceImpl implements QiniuService {
    private static final Set<String> ALLOWED = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp");
    private final QiniuProperties qiniuProperties;
    private final UploadManager uploadManager;
    private final Auth qiniuAuth;

    public QiniuServiceImpl(QiniuProperties properties, UploadManager manager, Auth auth) {
        this.qiniuProperties = properties; this.uploadManager = manager; this.qiniuAuth = auth;
    }

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("上传的文件不能为空");
        if (!qiniuProperties.isConfigured()) throw new BusinessException("七牛云参数未配置");
        String name = file.getOriginalFilename();
        if (name == null || !name.contains(".")) throw new BusinessException("上传的文件名不合法");
        String suffix = name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(suffix)) throw new BusinessException("不支持的文件格式");
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "images/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
        try {
            Auth auth = Auth.create(qiniuProperties.getAccessKey(), qiniuProperties.getSecretKey());
            Response response = uploadManager.put(file.getBytes(), key, auth.uploadToken(qiniuProperties.getBucket()));
            if (!response.isOK()) throw new BusinessException("图片上传至七牛云失败: " + response.error);
            DefaultPutRet ret = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
            String domain = qiniuProperties.getDomainUrl();
            if (domain == null || domain.isBlank()) return ret.key;
            domain = domain.trim();
            if (!domain.endsWith("/")) domain += "/";
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) domain = "http://" + domain;
            String publicUrl = domain + ret.key;

            // 兼容私有空间：自动签发10年有效期的安全下载 Token，彻底杜绝 401 download token not specified
            try {
                return auth.privateDownloadUrl(publicUrl, 315360000L);
            } catch (Exception e) {
                return publicUrl;
            }
        } catch (QiniuException e) {
            throw new BusinessException("七牛云存储异常");
        } catch (IOException e) {
            throw new BusinessException("读取上传文件内容失败: " + e.getMessage());
        }
    }
}
