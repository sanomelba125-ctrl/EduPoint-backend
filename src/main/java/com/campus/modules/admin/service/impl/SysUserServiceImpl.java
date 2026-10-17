package com.campus.modules.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.PasswordUtil;
import com.campus.modules.admin.dto.*;
import com.campus.modules.admin.entity.SysUser;
import com.campus.modules.admin.enums.SysUserStatus;
import com.campus.modules.admin.mapper.SysUserMapper;
import com.campus.modules.admin.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
                        SysUser::getPhone, SysUser::getStatus, SysUser::getCreateTime, SysUser::getUpdateTime)
                .eq(SysUser::getUsername, loginDTO.getUsername().trim())
                .one();

        // 2. 账号存在性校验(遵循安全模糊提示原则，避免用户名枚举探测)
        if (user == null) {
            log.warn("管理员登录失败: 用户名 [{}] 不存在", loginDTO.getUsername());
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), "用户名或密码错误");
        }

        // 3. 账号状态检查
        if (user.getStatus() != null && user.getStatus() == SysUserStatus.DISABLED.getCode()) {
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
        log.info("管理员 [{}] (ID: {}) 登录成功", user.getUsername(), user.getId());

        // 6. 组装脱敏后的返回视图对象
        SysUserVO userVO = convertToVO(user);

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
        if (user == null || (user.getStatus() != null && user.getStatus() == SysUserStatus.DISABLED.getCode())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号不存在或已被禁用");
        }

        return convertToVO(user);
    }

    @Override
    public IPage<SysUserVO> getUserPage(SysUserQueryDTO queryDTO) {
        Page<SysUser> page = new Page<>(
                queryDTO.getPage() != null ? queryDTO.getPage() : 1,
                queryDTO.getSize() != null ? queryDTO.getSize() : 10
        );

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        // 关键字多字段模糊检索
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String kw = queryDTO.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw).or().like(SysUser::getNickname, kw));
        }

        // 精确/模糊条件过滤
        if (StringUtils.hasText(queryDTO.getUsername())) {
            wrapper.like(SysUser::getUsername, queryDTO.getUsername().trim());
        }
        if (StringUtils.hasText(queryDTO.getNickname())) {
            wrapper.like(SysUser::getNickname, queryDTO.getNickname().trim());
        }
        if (StringUtils.hasText(queryDTO.getRole())) {
            wrapper.eq(SysUser::getRole, queryDTO.getRole().trim());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, queryDTO.getStatus());
        }

        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> userPage = page(page, wrapper);
        return userPage.convert(this::convertToVO);
    }

    @Override
    public SysUserVO createUser(SysUserCreateDTO createDTO) {
        String username = createDTO.getUsername().trim();

        // 1. 账号唯一性检查（忽略逻辑删除，防止碰撞底层 uk_username 唯一索引抛 500）
        SysUser existingUser = baseMapper.selectByUsernameIgnoreDeleted(username);
        if (existingUser != null) {
            if (java.util.Objects.equals(existingUser.getDeleted(), 1)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该管理员登录账号已被注销归档（历史占用），不可重复使用: " + username);
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该管理员登录账号已存在: " + username);
        }

        // 2. 密码加密处理（默认初始密码 123）
        String rawPassword = StringUtils.hasText(createDTO.getPassword()) ? createDTO.getPassword().trim() : "123";
        String encodedPassword = PasswordUtil.encode(rawPassword);

        // 3. 构建并保存实体
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setNickname(createDTO.getNickname().trim());
        user.setRole(createDTO.getRole().trim());
        user.setPhone(StringUtils.hasText(createDTO.getPhone()) ? createDTO.getPhone().trim() : null);
        user.setAvatar(StringUtils.hasText(createDTO.getAvatar()) ? createDTO.getAvatar().trim() : null);
        user.setStatus(SysUserStatus.ENABLED.getCode()); // 默认启用
        user.setDeleted(0);

        save(user);
        log.info("新增管理员成功: [{}] (ID: {}, Role: {})", user.getUsername(), user.getId(), user.getRole());

        // 重新获取完整信息并返回脱敏对象
        return convertToVO(getById(user.getId()));
    }

    @Override
    public SysUserVO updateUser(SysUserUpdateDTO updateDTO) {
        SysUser user = getById(updateDTO.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标管理员不存在");
        }

        boolean needKickout = false;

        if (StringUtils.hasText(updateDTO.getNickname())) {
            user.setNickname(updateDTO.getNickname().trim());
        }

        // 角色修改限制：不能剥夺超级管理员的 SUPER_ADMIN 权限
        if (StringUtils.hasText(updateDTO.getRole())) {
            String targetRole = updateDTO.getRole().trim();
            if (isSuperAdmin(user) && !"SUPER_ADMIN".equalsIgnoreCase(targetRole)) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "系统核心超级管理员角色不可被降权修改");
            }
            if (!targetRole.equalsIgnoreCase(user.getRole())) {
                user.setRole(targetRole);
                needKickout = true; // 角色权限变更，踢下线以刷新会话权限
            }
        }

        if (updateDTO.getPhone() != null) {
            user.setPhone(StringUtils.hasText(updateDTO.getPhone()) ? updateDTO.getPhone().trim() : null);
        }

        if (updateDTO.getAvatar() != null) {
            user.setAvatar(StringUtils.hasText(updateDTO.getAvatar()) ? updateDTO.getAvatar().trim() : null);
        }

        // 状态修改限制：禁止禁用超级管理员
        if (updateDTO.getStatus() != null) {
            if (isSuperAdmin(user) && updateDTO.getStatus() == SysUserStatus.DISABLED.getCode()) {
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "禁止禁用系统超级管理员账号");
            }
            if (updateDTO.getStatus() == SysUserStatus.DISABLED.getCode() && (user.getStatus() == null || user.getStatus() != SysUserStatus.DISABLED.getCode())) {
                needKickout = true; // 账号被禁用，立即踢下线
            }
            user.setStatus(updateDTO.getStatus());
        }

        // 若传入了新密码，则进行加密更新，并强制旧会话下线
        if (StringUtils.hasText(updateDTO.getPassword())) {
            user.setPassword(PasswordUtil.encode(updateDTO.getPassword().trim()));
            needKickout = true;
        }

        updateById(user);

        if (needKickout) {
            StpUtil.kickout(ADMIN_LOGIN_PREFIX + user.getId());
            log.info("管理员安全属性变更，强制踢下线: (ID: {}, Username: {})", user.getId(), user.getUsername());
        } else {
            log.info("管理员信息更新成功: (ID: {}, Username: {})", user.getId(), user.getUsername());
        }

        return convertToVO(getById(user.getId()));
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标管理员不存在");
        }

        if (isSuperAdmin(user) && status != null && status == SysUserStatus.DISABLED.getCode()) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "禁止禁用系统超级管理员账号");
        }

        user.setStatus(status);
        updateById(user);

        // 核心安全修复：禁用账号时立即强踢当前管理员下线，注销 Token
        if (status != null && status == SysUserStatus.DISABLED.getCode()) {
            StpUtil.kickout(ADMIN_LOGIN_PREFIX + id);
            log.warn("管理员账号已禁用并强制踢下线: (ID: {}, Username: {})", id, user.getUsername());
        } else {
            log.info("管理员账号状态更新: (ID: {}, Username: {}, Status: {})", user.getId(), user.getUsername(), status);
        }
    }

    @Override
    public void deleteUser(Long id, Long currentUserId) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标管理员不存在");
        }

        if (isSuperAdmin(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "禁止注销/删除系统超级管理员账号");
        }

        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "禁止删除当前登录的操作员自身账号");
        }

        removeById(id);

        // 核心安全修复：账号删除/注销后立即强制踢下线并清除全部活跃会话
        StpUtil.kickout(ADMIN_LOGIN_PREFIX + id);
        log.info("管理员账号注销成功并强制踢下线: (ID: {}, Username: {})", user.getId(), user.getUsername());
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.campus.config.DefaultPasswordProvider defaultPasswordProvider;

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "目标管理员不存在");
        }

        // 核心安全防御：禁止重置系统内置超级管理员账号密码
        if (isSuperAdmin(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "禁止重置系统内置超级管理员账号密码");
        }

        String rawPassword = StringUtils.hasText(newPassword)
                ? newPassword.trim()
                : (defaultPasswordProvider != null ? defaultPasswordProvider.get() : "123456");
        String encodedPassword = PasswordUtil.encode(rawPassword);

        lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encodedPassword)
                .update();

        // 核心安全修复：重置密码后立即强制踢下线，旧 Token 立即作废，必须重新登录
        StpUtil.kickout(ADMIN_LOGIN_PREFIX + id);
        log.info("管理员密码重置成功并强制踢下线: (ID: {}, Username: {})", user.getId(), user.getUsername());
    }

    /**
     * 判断是否为系统内建超级管理员账号
     */
    private boolean isSuperAdmin(SysUser user) {
        if (user == null) return false;
        return Long.valueOf(1L).equals(user.getId()) || "admin".equalsIgnoreCase(user.getUsername());
    }

    /**
     * 实体转脱敏安全视图对象
     */
    private SysUserVO convertToVO(SysUser user) {
        if (user == null) return null;
        return SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}
