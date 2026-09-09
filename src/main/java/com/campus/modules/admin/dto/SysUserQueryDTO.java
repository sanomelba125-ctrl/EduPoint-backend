package com.campus.modules.admin.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台管理员列表多条件分页查询参数 DTO
 */
@Data
public class SysUserQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 10 */
    private Integer size = 10;

    /** 检索关键字（用户名或姓名昵称模糊匹配） */
    private String keyword;

    /** 用户名模糊过滤 */
    private String username;

    /** 昵称模糊过滤 */
    private String nickname;

    /** 角色权限过滤 (SUPER_ADMIN / ADMIN / COUNSELOR) */
    private String role;

    /** 账号状态过滤 (0-禁用, 1-启用) */
    private Integer status;
}
