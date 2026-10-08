package com.campus.config;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 对象存储客户端配置类 (支持优雅降级与本地模式)
 */
@Slf4j
@org.springframework.context.annotation.Configuration
public class QiniuConfig {

    private final QiniuProperties qiniuProperties;

    public QiniuConfig(QiniuProperties qiniuProperties) {
        this.qiniuProperties = qiniuProperties;
    }

    @PostConstruct
    public void init() {
        if (qiniuProperties.isQiniuEnabled()) {
            log.info("[OSS] 当前运行在七牛云 Kodo 对象存储模式 (bucket={})", qiniuProperties.getBucket());
        } else {
            log.info("[OSS] 当前运行在纯本地磁盘存储模式 (campus.oss.type={})", qiniuProperties.getType());
        }
    }

    /**
     * 构建七牛云上传管理器 UploadManager
     * 仅在 campus.oss.type 配置为 qiniu 时由 Spring 条件装配，本地存储模式不注册该 Bean
     */
    @Bean
    @ConditionalOnProperty(name = "campus.oss.type", havingValue = "qiniu")
    public UploadManager uploadManager() {
        Configuration cfg = new Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }

    /**
     * 构建七牛云认证凭证 Auth
     * 仅在 campus.oss.type 配置为 qiniu 时由 Spring 条件装配，本地存储模式不注册该 Bean
     */
    @Bean
    @ConditionalOnProperty(name = "campus.oss.type", havingValue = "qiniu")
    public Auth qiniuAuth() {
        return Auth.create(qiniuProperties.getAccessKey(), qiniuProperties.getSecretKey());
    }
}
