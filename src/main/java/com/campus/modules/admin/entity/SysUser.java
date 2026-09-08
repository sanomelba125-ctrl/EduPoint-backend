package com.campus.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员/后台用户实体
 * 对应表: sys_user
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名(唯一标识) */
    private String username;

    /**
     * 登录密码防护:
     * 1. @TableField(select = false): MyBatis-Plus 默认不查询，防止业务SQL泄露
     * 2. @JsonProperty(access = JsonProperty.Access.WRITE_ONLY): 序列化时忽略，防止返回给前端
     */
    @TableField(select = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 姓名/显示昵称 */
    private String nickname;

    /** 用户头像URL */
    private String avatar;

    /** 角色权限: SUPER_ADMIN-超级管理员, ADMIN-普通管理员, COUNSELOR-辅导员 */
    private String role;

    /** 联系手机号 */
    private String phone;

    /** 账号状态: 0-禁用 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-正常 1-已删除 */
    private Integer deleted;
}