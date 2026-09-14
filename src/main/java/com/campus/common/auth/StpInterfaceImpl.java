package com.campus.common.auth;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.constant.UserRole;
import com.campus.modules.admin.entity.SysUser;
import com.campus.modules.admin.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 框架核心权限与角色扩展实现
 * 自动接管 @SaCheckRole 与 @SaCheckPermission 注解切面，将业务身份上升为框架级 RBAC 强约束
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    /**
     * 获取指定账号所拥有的权限码列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 当前项目以基于角色的访问控制 (RBAC) 为主，预留权限码扩展
        return Collections.emptyList();
    }

    /**
     * 获取指定账号所拥有的角色标识列表
     * 当 Controller 标注 @SaCheckRole 时，框架 AOP 切面在方法执行前自动调用此接口核验
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        String idStr = String.valueOf(loginId);

        // 1. 学生端账号 (student:xxx) -> 赋予单一 STUDENT 角色
        if (idStr.startsWith(AuthUtil.STUDENT_PREFIX)) {
            return List.of(UserRole.STUDENT);
        }

        // 2. 管理端账号 (admin:xxx) -> 解析真实管理角色 (SUPER_ADMIN / ADMIN / COUNSELOR)
        if (idStr.startsWith(AuthUtil.ADMIN_PREFIX)) {
            try {
                // 优先从活跃的 SaSession 中快速读取 (无额外 DB I/O)
                SaSession session = StpUtil.getSessionByLoginId(loginId, false);
                if (session != null) {
                    Object roleObj = session.get("role");
                    if (roleObj != null && StringUtils.hasText(roleObj.toString())) {
                        return List.of(roleObj.toString());
                    }
                }

                // 缓存未命中时安全回源数据库查询
                Long adminId = Long.valueOf(idStr.substring(AuthUtil.ADMIN_PREFIX.length()));
                SysUser user = sysUserMapper.selectById(adminId);
                if (user != null && user.getDeleted() != 1 && StringUtils.hasText(user.getRole())) {
                    if (session != null) {
                        session.set("role", user.getRole());
                    }
                    return List.of(user.getRole());
                }
            } catch (Exception e) {
                log.warn("[RBAC角色解析告警] 获取管理员角色异常: loginId={}, err={}", loginId, e.getMessage());
            }
        }

        return Collections.emptyList();
    }
}
