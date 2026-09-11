package com.campus.modules.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.student.entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 原子扣减可用积分（必须确保扣减后余额 >= 0）
     */
    @Update("UPDATE student SET current_points = current_points - #{points} " +
            "WHERE id = #{id} AND current_points >= #{points} AND deleted = 0")
    int deductCurrentPoints(@Param("id") Long id, @Param("points") Integer points);

    /**
     * 原子返还/增加可用积分
     */
    @Update("UPDATE student SET current_points = current_points + #{points} " +
            "WHERE id = #{id} AND deleted = 0")
    int addCurrentPoints(@Param("id") Long id, @Param("points") Integer points);

    /**
     * 原子增加历史累计总积分
     */
    @Update("UPDATE student SET total_points = total_points + #{points} " +
            "WHERE id = #{id} AND deleted = 0")
    int addTotalPoints(@Param("id") Long id, @Param("points") Integer points);

    /**
     * 精确计算学生在全校光荣榜上的排位名次（总积分降序，学号升序稳定排序）
     */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) + 1 FROM student " +
            "WHERE deleted = 0 AND ((total_points > #{totalPoints}) OR (total_points = #{totalPoints} AND student_no < #{studentNo}))")
    int calculateStudentRank(@Param("totalPoints") Integer totalPoints, @Param("studentNo") String studentNo);

    /**
     * 查询学生当前可用积分余额
     */
    @org.apache.ibatis.annotations.Select("SELECT current_points FROM student WHERE id = #{id} AND deleted = 0")
    Integer selectCurrentPoints(@Param("id") Long id);
}
