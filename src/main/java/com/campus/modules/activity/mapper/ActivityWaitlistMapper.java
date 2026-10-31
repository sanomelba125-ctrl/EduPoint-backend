package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.ActivityWaitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivityWaitlistMapper extends BaseMapper<ActivityWaitlist> {

    /**
     * 锁定读取当前活动候补队列的队尾序号。
     *
     * <p><b>为什么不能用 {@code SELECT MAX(...)}：</b>报名事务运行在 REPEATABLE READ 下，
     * 一致性读使用事务第一次查询时建立的快照。即使 activity 行锁把并发串行化了，
     * 后一个事务的普通 {@code SELECT MAX(queue_number)} 仍看不到前一个事务刚刚提交的候补行，
     * 于是所有并发请求都会算出同一个 {@code 0 + 1 = 1}。2026-10-31 实测 3 个并发候补全部为 1。</p>
     *
     * <p>改为 {@code ORDER BY ... DESC LIMIT 1 FOR UPDATE}：这是当前读，会读取最新已提交版本，
     * 并配合调用方持有的 activity 行锁保证「读取队尾 + 插入新行」串行执行。</p>
     *
     * <p>查询列与索引 {@code idx_act_queue_cover(activity_id, status, deleted, queue_number)}
     * 完全匹配，仍走覆盖索引反向扫描，不产生 filesort。</p>
     *
     * @return 当前最大有效序号；队列为空返回 {@code null}
     */
    @Select("SELECT queue_number FROM activity_waitlist " +
            "WHERE activity_id = #{activityId} AND status = 1 AND deleted = 0 " +
            "ORDER BY queue_number DESC LIMIT 1 FOR UPDATE")
    Integer selectTailQueueNumberForUpdate(@Param("activityId") Long activityId);

    /**
     * 当前读统计「排在该序号之前（含自己）的有效候补人数」，用于返回用户可读的实时排位。
     *
     * <p>不能使用普通 {@code SELECT COUNT(*)}：REPEATABLE READ 下它读事务开始时的快照，
     * 并发入队时会看不到前一个事务刚插入的候补行，导致 3 个不同序号都显示「第 1 位」。</p>
     */
    @Select("SELECT COUNT(*) FROM activity_waitlist " +
            "WHERE activity_id = #{activityId} AND status = 1 AND deleted = 0 " +
            "AND queue_number <= #{queueNumber} FOR UPDATE")
    Long countActiveAheadForUpdate(@Param("activityId") Long activityId,
                                   @Param("queueNumber") Integer queueNumber);
}
