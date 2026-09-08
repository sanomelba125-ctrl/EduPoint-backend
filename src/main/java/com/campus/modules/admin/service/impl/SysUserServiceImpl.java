package com.campus.modules.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PasswordUtil;
import com.campus.modules.admin.dto.AdminLoginDTO;
import com.campus.modules.admin.dto.AdminLoginVO;
import com.campus.modules.admin.dto.SysUserVO;
import com.campus.modules.admin.entity.SysUser;
import com.campus.modules.admin.mapper.SysUserMapper;
import com.campus.modules.admin.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 系统用户与管理员服务实现类
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    private static final String ADMIN_LOGIN_PREFIX = "admin:";

    @Override
    public AdminLoginVO login(AdminLoginDTO loginDTO) {
        // 1. 显式查询包含密码列的记录(@TableField(select = false)默认排除了密码)
        SysUser user = lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getPassword,
                        SysUser::getNickname, SysUser::getAvatar, SysUser::getRole,
                        SysUser::getPhone, SysUser::getStatus)
                .eq(SysUser::getUsername, loginDTO.getUsername().trim())
                .one();

        // 2. 账号存在性校验(遵循安全模糊提示原则，避免用户名枚举探测)
        if (user == null) {
            log.warn("管理员登录失败: 用户名 [{}] 不存在", loginDTO.getUsername());
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "用户名或密码错误");
        }

        // 3. 账号状态检查
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("管理员登录失败: 账号 [{}] 已被禁用", loginDTO.getUsername());
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "该管理员账号已被禁用，请联系超级管理员");
        }

        // 4. BCrypt 密文哈希比对
        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("管理员登录失败: 账号 [{}] 密码不匹配", loginDTO.getUsername());
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "用户名或密码错误");
        }

        // 5. Sa-Token 执行登录并写入会话
        String loginId = ADMIN_LOGIN_PREFIX + user.getId();
        StpUtil.login(loginId);
        String token = StpUtil.getTokenValue();
        StpUtil.getSession().set("userType", "ADMIN");
        StpUtil.getSession().set("userId", user.getId());
        StpUtil.getSession().set("role", user.getRole());
        log.info("管理员 [{}] (ID: {}) 登录成功, 获得 Token: {}", user.getUsername(), user.getId(), token);

        // 6. 组装脱敏后的返回视图对象
        SysUserVO userVO = SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .phone(user.getPhone())
                .build();

        return AdminLoginVO.builder()
                .token(token)
                .user(userVO)
                .userInfo(userVO)
                .build();
    }

    @Override
    public void logout() {
        if (StpUtil.isLogin()) {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            StpUtil.logout();
            log.info("管理员 (ID: {}) 退出登录成功", loginId);
        }
    }

    @Override
    public SysUserVO getCurrentUserInfo() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();

        // 严格校验用户类型，防止学生端 Token 越权调用管理端接口
        if (!loginId.startsWith(ADMIN_LOGIN_PREFIX)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问管理端接口");
        }

        long userId = Long.parseLong(loginId.substring(ADMIN_LOGIN_PREFIX.length()));
        SysUser user = getById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号不存在或已被禁用");
        }

        return SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .phone(user.getPhone())
                .build();
    }
}