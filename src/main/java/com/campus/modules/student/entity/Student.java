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

    // 注意：不要为 status 再写一个「参数是枚举」的 setStatus 重载。
    // Jackson 把重载视为同一属性 status 的冲突 setter，一旦给其中一个加 @JsonIgnore，
    // 整个 status 属性会连同 getter 一起被丢弃——接口会直接少字段。
    // 需要枚举入参请用 setStatusEnum(...)。
    public void setStatusEnum(com.campus.modules.student.enums.StudentStatus status) {
        setStatus(status != null ? status.getCode() : null);
    }
}
