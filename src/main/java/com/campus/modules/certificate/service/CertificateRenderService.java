package com.campus.modules.certificate.service;


/**
 * 电子荣誉证书动态渲染与存储引擎接口
 * 对应 P2-12：基于 Java Graphics2D 像素级合成模板、学生信息、公章与防伪二维码
 */
public interface CertificateRenderService {

    /**
     * 渲染证书图片并上传/保存，回写 certificate.file_url
     * @param certificateId 证书ID
     * @return 最终文件访问地址
     */
    String renderAndUpload(Long certificateId);

    /**
     * 渲染指定证书编号为 PNG 二进制图片流（支持本地实时回显与下载）
     * @param certificateNo 证书编号
     * @return PNG 图片二进制字节数组
     */
    byte[] renderToPngBytesByNo(String certificateNo);
}
