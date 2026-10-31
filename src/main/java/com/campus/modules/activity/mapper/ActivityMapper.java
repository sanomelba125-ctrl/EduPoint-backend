package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    /**
     * 条件原子占用一个正式报名名额。
     *
     * <p><b>为什么不再用 version CAS：</b>{@code signup()} 运行在 REPEATABLE READ 事务内，
     * version 冲突后再次 {@code selectById} 读到的仍是本事务开始时的快照，看不到其他事务
     * 已提交的新 version，重试必然全部失败。调用方随后会把「还有名额」误判成「名额已满」
     * 并写入候补队列（2026-10-31 实测：限额 10、5 个并发只成功 1 人）。
     * 现在把「名额是否足够」和「自增」放在同一条 UPDATE 中，由 InnoDB 行锁串行化，
     * 不再需要基于旧快照的循环重试；锁内最多再做一次当前读条件自增，用于兜住“等待锁期间名额刚被释放”的窗口。</p>
     *
     * <p>{@code end_time > NOW()} 是第二道时间闸门：活动状态每分钟才流转一次，
     * 不能只依赖 status 字段，否则结束瞬间仍可能挤出名额。</p>
     *
     * @return 1=成功占用；0=名额已满、活动已结束/归档或记录不存在
     */
    @Update("UPDATE activity SET participant_count = COALESCE(participant_count,0) + 1, version = version + 1 " +
            "WHERE id=#{id} AND deleted=0 AND status IN (0,1) " +
            "AND end_time > NOW() " +
            "AND (COALESCE(registration_limit,0) = 0 OR participant_count < registration_limit)")
    int incrementParticipantIfAvailable(@Param("id") Long id);

    @Update("UPDATE activity SET participant_count = GREATEST(COALESCE(participant_count,0) - 1, 0), version = version + 1 WHERE id=#{id} AND deleted=0")
    int decrementParticipant(@Param("id") Long id);

    /**
     * 聚合统计各分类活动数量
     */
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(category, 'OTHER') AS category, COUNT(*) AS cnt FROM activity WHERE deleted = 0 GROUP BY category")
    java.util.List<java.util.Map<String, Object>> countGroupByCategory();
}
