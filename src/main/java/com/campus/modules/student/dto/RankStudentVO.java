package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 积分光荣榜单条记录视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankStudentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 排名名次 (从 1 开始) */
    private Integer rank;

    /** 学生 ID */
    private Long studentId;

    /** 学号 */
    private String studentNo;

    /** 姓名 */
    private String name;

    /** 头像 URL */
    private String avatar;

    /** 学院 */
    private String college;

    /** 专业班级 */
    private String className;

    /** 历史累计学分 (total_points) */
    private Integer totalPoints;

    /** 可用积分 (current_points) */
    private Integer currentPoints;

    /** 是否为当前登录学生本人 */
    private Boolean isMe;
}
