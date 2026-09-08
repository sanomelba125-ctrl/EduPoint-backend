package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生档案与个人中心展示视图对象 (脱敏输出)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String studentNo;
    private String name;
    private Integer gender;
    private String avatar;
    private String openid;
    private String college;
    private String major;
    private String className;
    private String phone;
    private Integer totalPoints;
    private Integer currentPoints;
    private Integer status;
    private LocalDateTime createTime;
}
