package com.campus.modules.system.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 七牛云对象存储文件服务接口
 */
public interface QiniuService {

    /**
     * 上传图片并返回访问 URL
     *
     * @param file 图片文件
     * @return 文件的外链访问 URL
     */
    String uploadImage(MultipartFile file);

    /**
     * 为私有空间或敏感文件生成限时安全的专属访问 URL（防伪防盗链）
     * 专用于后续“电子荣誉证书”、“敏感证明材料”等涉密场景
     *
     * @param targetUrlOrKey 原始文件 URL 或存储 Key
     * @param expireSeconds  有效时长（单位：秒，如 300 表示 5 分钟有效）
     * @return 携带时效加密签名的安全访问 URL
     */
    String generatePresignedUrl(String targetUrlOrKey, long expireSeconds);
}
