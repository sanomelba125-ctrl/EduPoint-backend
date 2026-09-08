package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 光荣榜完整数据结构（包含排名前列榜单与当前用户自身排名位次）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRankResultVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 榜单记录列表 */
    private List<RankStudentVO> records;

    /** 榜单总人数 */
    private Long total;

    /** 当前登录学生本人位次数据 */
    private RankStudentVO myRank;

    /** 距离前一名同学分差 (若是第 1 名则为 0) */
    private Integer diffToPrevious;

    /** 前一名同学姓名 (若有) */
    private String previousStudentName;
}
