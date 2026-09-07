package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员/后台用户实体
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * 敏感字段防护：
     * 1. @TableField(select = false)：MyBatis-Plus 查询时默认不查该字段，防止内存与 SQL 泄露
     * 2. @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)：仅反序列化生效，接口返回 JSON 时绝不输出该字段
     */
    @TableField(select = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;
    private String avatar;
    private String role;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
