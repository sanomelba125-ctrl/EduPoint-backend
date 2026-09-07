package com.campus.modules.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.modules.admin.entity.SysUser;
import com.campus.modules.admin.mapper.SysUserMapper;
import com.campus.modules.admin.service.SysUserService;
import org.springframework.stereotype.Service;

/**
 * SysUser 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    // 实现业务方法
}
