package com.campus.modules.activity;

import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.ActivityWaitlistMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 活动报名并发的 SQL 契约测试（F1，2026-10-31）。
 *
 * <p>服务层单测用 Mockito 把 Mapper 全替换掉了，无法发现「SQL 仍然用 version 条件」
 * 或「队尾查询又改回 REPEATABLE READ 快照读」这类回归。这里直接读取注解里的 SQL 文本锁死契约。</p>
 */
class ActivitySignupSqlContractTest {

    private String updateSql(String method) throws Exception {
        return ActivityMapper.class
                .getMethod(method, Long.class)
                .getAnnotation(Update.class)
                .value()[0];
    }

    private String selectSql(Class<?> mapper, String method, Class<?>... params) throws Exception {
        return mapper.getMethod(method, params)
                .getAnnotation(Select.class)
                .value()[0];
    }

    @Test
    @DisplayName("名额占用必须是单条条件 UPDATE：名额判断与自增同句完成，不得再用 version CAS")
    void incrementParticipantMustBeConditionalAtomicUpdate() throws Exception {
        String sql = updateSql("incrementParticipantIfAvailable").toLowerCase();

        assertTrue(sql.contains("participant_count = coalesce(participant_count,0) + 1"),
                "必须在数据库当前值上自增，不能读快照后写回");
        assertTrue(sql.contains("participant_count < registration_limit"),
                "名额判断必须与 UPDATE 同一句，否则并发会超卖");
        assertTrue(sql.contains("status in (0,1)"), "只允许未开始/进行中的活动占用名额");
        assertTrue(sql.contains("end_time > now()"),
                "活动状态每分钟才流转一次，必须用 end_time 作为第二道时间闸门");
        assertFalse(sql.contains("version = #{version}"),
                "不得回退到事务快照里永远失败的 version CAS");
        assertFalse(sql.contains("version=#{version}"),
                "不得回退到事务快照里永远失败的 version CAS");
    }

    @Test
    @DisplayName("候补队尾查询必须 FOR UPDATE 当前读，且不能再使用 MAX 聚合快照读")
    void tailQueueMustUseLockingCurrentRead() throws Exception {
        String sql = selectSql(ActivityWaitlistMapper.class,
                "selectTailQueueNumberForUpdate", Long.class).toLowerCase();

        assertTrue(sql.contains("for update"),
                "REPEATABLE READ 下普通 SELECT 看不到并发事务刚插入的候补行，必须当前读");
        assertTrue(sql.contains("order by queue_number desc"),
                "必须取最大有效序号作为队尾");
        assertTrue(sql.contains("status = 1") && sql.contains("deleted = 0"),
                "只能统计仍在候补中的有效记录");
        assertFalse(sql.contains("max("),
                "MAX 聚合在 REPEATABLE READ 快照下会重复返回旧队尾，改回即回归");
    }

    @Test
    @DisplayName("候补实时排位统计也必须 FOR UPDATE 当前读")
    void rankCountMustUseLockingCurrentRead() throws Exception {
        String sql = selectSql(ActivityWaitlistMapper.class,
                "countActiveAheadForUpdate", Long.class, Integer.class).toLowerCase();

        assertTrue(sql.contains("count(*)"), "排位按有效候补人数统计");
        assertTrue(sql.contains("queue_number <= #{queuenumber}"), "统计排在自己之前（含自己）的有效候补");
        assertTrue(sql.contains("for update"),
                "普通 COUNT 使用事务旧快照，会让并发入队者都显示第 1 位");
    }
}
