package com.campus.modules.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生档案与账户核心实体
 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String studentNo;

    /**
     * 敏感字段防护：
     * 1. @TableField(select = false)：MyBatis-Plus 查询时默认不查该字段
     * 2. @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)：返回 JSON 时脱敏
     */
    @TableField(select = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

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
    private LocalDateTime updateTime;
    private Integer deleted;
}
