package com.campus.modules.system.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 七牛云对象存储文件服务接�? */
public interface QiniuService {

    /**
     * 上传图片并返回外链访�?URL
     *
     * @param file 图片文件
     * @return 完整的图片访�?URL
     */
    String uploadImage(MultipartFile file);
}
