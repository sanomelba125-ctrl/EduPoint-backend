package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityCheckinCodeMapper extends BaseMapper<ActivityCheckinCode> {
    @Update("UPDATE activity_checkin_code SET status=1, used_by_student_id=#{studentId}, used_time=NOW() WHERE id=#{id} AND status=0 AND expire_time>NOW()")
    int consume(@Param("id") Long id, @Param("studentId") Long studentId);
}
