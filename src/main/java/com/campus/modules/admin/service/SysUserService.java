package com.campus.modules.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.admin.dto.AdminLoginDTO;
import com.campus.modules.admin.dto.AdminLoginVO;
import com.campus.modules.admin.dto.SysUserVO;
import com.campus.modules.admin.entity.SysUser;

/**
 * 系统用户与管理员服务接口
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 管理员登录认证
     *
     * @param loginDTO 登录凭据
     * @return 登录结果(包含Token及用户信息)
     */
    AdminLoginVO login(AdminLoginDTO loginDTO);

    /**
     * 管理员退出登录
     */
    void logout();

    /**
     * 获取当前登录的管理员信息
     *
     * @return 管理员脱敏视图对象
     */
    SysUserVO getCurrentUserInfo();
}