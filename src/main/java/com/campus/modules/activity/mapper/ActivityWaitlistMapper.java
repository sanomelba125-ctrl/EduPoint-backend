package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.ActivityWaitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityWaitlistMapper extends BaseMapper<ActivityWaitlist> {

    /**
     * P2-7: 原子聚合查询当前活动候补队列的最大排队序号（避免锁内全表扫描与内存流式计算）
     */
    @Select("SELECT COALESCE(MAX(queue_number), 0) FROM activity_waitlist " +
            "WHERE activity_id = #{activityId} AND status = 1 AND deleted = 0")
    Integer selectMaxQueueNumber(@Param("activityId") Long activityId);
}
