package com.campus.modules.student.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 学生修改本人资料入参 (契合 API.md 1.7)
 */
@Data
public class StudentUpdateProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 姓名/昵称
     */
    private String name;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 头像图片 URL
     */
    private String avatar;

    /**
     * 性别: 0-未知 1-男 2-女
     */
    private Integer gender;
}
