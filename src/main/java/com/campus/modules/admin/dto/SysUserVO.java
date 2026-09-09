package com.campus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员脱敏安全展示视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private String phone;
    private Integer status;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}