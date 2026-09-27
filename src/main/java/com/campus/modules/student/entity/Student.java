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
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer deleted;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.campus.modules.student.enums.StudentStatus getStatusEnum() {
        return com.campus.modules.student.enums.StudentStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setStatus(com.campus.modules.student.enums.StudentStatus status) {
        this.status = status != null ? status.getCode() : null;
    }

    public void setStatusEnum(com.campus.modules.student.enums.StudentStatus status) {
        setStatus(status);
    }
}
