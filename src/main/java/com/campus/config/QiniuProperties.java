package com.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置属性类 (支持 campus.oss.type: local / qiniu)
 */
@Component
@ConfigurationProperties(prefix = "qiniu")
public class QiniuProperties {

    /**
     * 存储类型: local (本地磁盘存储) / qiniu (七牛云 Kodo 对象存储)
     */
    @Value("${campus.oss.type:local}")
    private String type = "local";

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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
     * 是否启用七牛云存储 (配置了 qiniu 类型且配置了合法的密钥)
     */
    public boolean isQiniuEnabled() {
        return "qiniu".equalsIgnoreCase(type) && isConfigured();
    }

    /**
     * 判断是否已配置合法的七牛云密钥
     */
    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank()
                && !accessKey.contains("your-access-key")
                && !accessKey.contains("your-real-access-key")
                && !"unconfigured".equalsIgnoreCase(accessKey)
                && secretKey != null && !secretKey.isBlank()
                && !secretKey.contains("your-secret-key")
                && !secretKey.contains("your-real-secret-key")
                && !"unconfigured".equalsIgnoreCase(secretKey)
                && bucket != null && !bucket.isBlank()
                && !bucket.contains("your-bucket-name")
                && !bucket.contains("your-real-bucket-name")
                && !"unconfigured".equalsIgnoreCase(bucket);
    }
}
