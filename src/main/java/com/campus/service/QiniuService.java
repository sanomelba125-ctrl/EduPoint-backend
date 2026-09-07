package com.campus.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 七牛云对象存储文件服务接口
 */
public interface QiniuService {

    /**
     * 上传图片并返回外链访问 URL
     *
     * @param file 图片文件
     * @return 完整的图片访问 URL
     */
    String uploadImage(MultipartFile file);
}
