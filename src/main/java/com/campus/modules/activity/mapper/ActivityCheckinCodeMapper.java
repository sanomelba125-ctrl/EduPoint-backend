package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityCheckinCodeMapper extends BaseMapper<ActivityCheckinCode> {

    /**
     * 记录一次签到码核验使用（大屏广播码模式下不改变 status 为 1，允许有效期内多名已报名学生扫码核验）。
     * 用于记录最后核验学生及核验时间，保留审计与追溯信息。
     */
    @Update("UPDATE activity_checkin_code "
          + "SET used_by_student_id=#{studentId}, used_time=NOW() "
          + "WHERE id=#{id} AND status=0 AND expire_time>NOW()")
    int markUsed(@Param("id") Long id, @Param("studentId") Long studentId);
}
