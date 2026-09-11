package com.campus.common.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;

/**
 * 统一鉴权工具类
 * 负责解析与校验 Sa-Token 的登录身份（学生端 student: 或 管理端 admin:）
 */
public final class AuthUtil {

    public static final String STUDENT_PREFIX = "student:";
    public static final String ADMIN_PREFIX = "admin:";

    private AuthUtil() {
    }

    /**
     * 要求必须为学生登录，返回学生 ID
     *
     * @return 学生 ID
     * @throws BusinessException 未登录或非学生身份时抛出 401/403
     */
    public static Long requireStudentId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith(STUDENT_PREFIX)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅限学生端账号访问");
        }
        try {
            return Long.valueOf(loginId.substring(STUDENT_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录状态无效");
        }
    }

    /**
     * 要求必须为管理员登录，返回管理员 ID
     *
     * @return 管理员 ID
     * @throws BusinessException 未登录或非管理员身份时抛出 401/403
     */
    public static Long requireAdminId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith(ADMIN_PREFIX)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问管理端接口");
        }
        try {
            return Long.valueOf(loginId.substring(ADMIN_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录状态无效");
        }
    }

    /**
     * 获取当前登录学生 ID，未登录或非学生身份则安全返回 null（适用于榜单高亮等非强制登录场景）
     *
     * @return 学生 ID 或 null
     */
    public static Long currentStudentIdOrNull() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId == null || !loginId.startsWith(STUDENT_PREFIX)) {
                return null;
            }
            return Long.valueOf(loginId.substring(STUDENT_PREFIX.length()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录管理员 ID，未登录或非管理员身份则安全返回 null
     *
     * @return 管理员 ID 或 null
     */
    public static Long currentAdminIdOrNull() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId == null || !loginId.startsWith(ADMIN_PREFIX)) {
                return null;
            }
            return Long.valueOf(loginId.substring(ADMIN_PREFIX.length()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前是否为学生登录
     */
    public static boolean isStudent() {
        if (!StpUtil.isLogin()) {
            return false;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            return loginId != null && loginId.startsWith(STUDENT_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 当前是否为管理员登录
     */
    public static boolean isAdmin() {
        if (!StpUtil.isLogin()) {
            return false;
        }
        try {
            String loginId = StpUtil.getLoginIdAsString();
            return loginId != null && loginId.startsWith(ADMIN_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 格式化学生登录标识
     */
    public static String formatStudentLoginId(Long studentId) {
        return STUDENT_PREFIX + studentId;
    }

    /**
     * 格式化管理员登录标识
     */
    public static String formatAdminLoginId(Long adminId) {
        return ADMIN_PREFIX + adminId;
    }
}
