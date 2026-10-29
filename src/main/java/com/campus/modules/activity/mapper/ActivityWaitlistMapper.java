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
     *
     * <p><b>为什么能命中覆盖索引（2026-10-29 修复）</b>：本查询的三个过滤列
     * （{@code activity_id / status / deleted}）与取值列 {@code queue_number}
     * 全部落在 V1.0.12 建的 {@code idx_act_queue_cover(activity_id, status, deleted, queue_number)} 里，
     * 优化器可直接在索引上定位到该前缀的最大值。实测 3013 行数据下执行计划为
     * {@code Select tables optimized away}，而修复前只能走 {@code uk_act_stu_wait(activity_id, student_id)}、
     * {@code Extra=Using where} 逐行回表判断 {@code status/deleted}。</p>
     *
     * <p><b>不要为了"优化"删掉 {@code AND deleted = 0}</b>：删掉同样能优化掉回表，
     * 但会把逻辑删除语义丢掉——一旦候补记录被软删就会被算进排队号。
     * 正确做法是让索引覆盖这个条件，而不是改写查询语义。</p>
     */
    @Select("SELECT COALESCE(MAX(queue_number), 0) FROM activity_waitlist " +
            "WHERE activity_id = #{activityId} AND status = 1 AND deleted = 0")
    Integer selectMaxQueueNumber(@Param("activityId") Long activityId);
}
