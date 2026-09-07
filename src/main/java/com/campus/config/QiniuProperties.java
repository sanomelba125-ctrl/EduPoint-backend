package com.campus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 七牛�?Kodo 对象存储配置属性类
 */
@Component
@ConfigurationProperties(prefix = "qiniu")
public class QiniuProperties {

    /**
     * 密钥 AccessKey (个人中心 -> 密钥管理)
     */
    private String accessKey;

    /**
     * 密钥 SecretKey (个人中心 -> 密钥管理)
     */
    private String secretKey;

    /**
     * 存储空间名称 Bucket (对象存储 Kodo -> 空间管理)
     */
    private String bucket;

    /**
     * 外链访问域名 (例如: http://xxxx.bkt.clouddn.com/)
     */
    private String domainUrl;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getDomainUrl() {
        return domainUrl;
    }

    public void setDomainUrl(String domainUrl) {
        this.domainUrl = domainUrl;
    }

    /**
     * 判断是否已配置合法的七牛云密�?     */
    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank() && !accessKey.contains("your-access-key")
                && secretKey != null && !secretKey.isBlank() && !secretKey.contains("your-secret-key")
                && bucket != null && !bucket.isBlank() && !bucket.contains("your-bucket-name");
    }
}
