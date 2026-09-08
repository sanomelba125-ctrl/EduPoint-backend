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
}
