package com.campus.modules.mall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.service.GiftService;
import org.springframework.stereotype.Service;

/**
 * Gift 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class GiftServiceImpl extends ServiceImpl<GiftMapper, Gift> implements GiftService {
    // 实现业务方法
}
