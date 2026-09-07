package com.campus.config;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.context.annotation.Bean;

/**
 * 七牛�?SDK 客户端配置类
 */
@org.springframework.context.annotation.Configuration
public class QiniuConfig {

    private final QiniuProperties qiniuProperties;

    public QiniuConfig(QiniuProperties qiniuProperties) {
        this.qiniuProperties = qiniuProperties;
    }

    /**
     * 构建七牛云上传管理器 UploadManager
     * 使用 Region.autoRegion() 自动适配对应机房存储区域
     */
    @Bean
    public UploadManager uploadManager() {
        Configuration cfg = new Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }

    /**
     * 构建七牛云认证凭�?Auth
     */
    @Bean
    public Auth qiniuAuth() {
        String ak = qiniuProperties.getAccessKey() != null ? qiniuProperties.getAccessKey() : "unconfigured";
        String sk = qiniuProperties.getSecretKey() != null ? qiniuProperties.getSecretKey() : "unconfigured";
        return Auth.create(ak, sk);
    }
}
