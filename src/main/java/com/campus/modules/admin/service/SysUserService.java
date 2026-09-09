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

    /**
     * 分页多条件查询系统管理员列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    com.baomidou.mybatisplus.core.metadata.IPage<SysUserVO> getUserPage(com.campus.modules.admin.dto.SysUserQueryDTO queryDTO);

    /**
     * 新增管理员账号
     *
     * @param createDTO 新增参数
     * @return 新增后的脱敏管理员对象
     */
    SysUserVO createUser(com.campus.modules.admin.dto.SysUserCreateDTO createDTO);

    /**
     * 修改管理员信息
     *
     * @param updateDTO 修改参数
     * @return 更新后的脱敏管理员对象
     */
    SysUserVO updateUser(com.campus.modules.admin.dto.SysUserUpdateDTO updateDTO);

    /**
     * 启用/停用管理员账号
     *
     * @param id     管理员ID
     * @param status 目标状态(0-禁用, 1-启用)
     */
    void updateUserStatus(Long id, Integer status);

    /**
     * 删除/注销管理员账号(逻辑删除)
     *
     * @param id            管理员ID
     * @param currentUserId 当前登录的操作人ID
     */
    void deleteUser(Long id, Long currentUserId);

    /**
     * 重置管理员密码
     *
     * @param id          管理员ID
     * @param newPassword 新密码
     */
    void resetPassword(Long id, String newPassword);
}