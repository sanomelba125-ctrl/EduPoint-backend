package com.campus.modules.certificate.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.certificate.service.CertificateService;
import org.springframework.stereotype.Service;

/**
 * Certificate 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class CertificateServiceImpl extends ServiceImpl<CertificateMapper, Certificate> implements CertificateService {
    // 实现业务方法
}
