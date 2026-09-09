package com.campus.modules.student.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端学生档案多条件分页查询参数 DTO
 */
@Data
public class StudentQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 10 */
    private Integer size = 10;

    /** 综合关键字（学号或姓名模糊检索） */
    private String keyword;

    /** 班级名称筛选 */
    private String className;

    /** 学院筛选 */
    private String college;

    /** 专业筛选 */
    private String major;

    /** 账号状态筛选 (0-冻结/禁用, 1-正常启用) */
    private Integer status;
}
