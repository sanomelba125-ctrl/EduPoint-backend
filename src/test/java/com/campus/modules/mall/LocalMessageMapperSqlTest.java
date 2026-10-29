package com.campus.modules.mall;

import com.campus.modules.mall.mapper.LocalMessageMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发件箱兜底扫描的 SQL 契约测试（2026-10-29，P1-2）。
 *
 * <p>背景：{@code selectStalePublishedMessages} 原先按 {@code create_time <= 阈值} 判断「投递后长期无消费」。
 * 但 {@code create_time} 永不变化，而重投链路会不断改写消息状态——
 * {@code resetToPending} 把 status 改回 0、{@code markAsPublished} 再改回 1。
 * 于是一条一小时前创建的消息被捞回重投后，5 秒后的下一轮扫描仍然命中（消费端还没写消费日志），
 * 25 秒内连投 5 次，把 {@code retry_count} 顶到上限直接判死并触发库存回补。
 *
 * <p>修法是把判据换成 {@code update_time}（列上带 {@code ON UPDATE CURRENT_TIMESTAMP}，
 * 且 {@code resetToPending} 显式写入），每次重投都重置观察窗。
 *
 * <p>这条不变量只能靠 SQL 文本保证，故用反射直接校验注解里的语句：
 * 一旦有人把判据改回 {@code create_time}，测试立刻失败。
 */
class LocalMessageMapperSqlTest {

    private static final String STALE_QUERY_METHOD = "selectStalePublishedMessages";

    @Test
    @DisplayName("P1-2：兜底扫描必须以 update_time 判定「长期无消费」，不能用永不变化的 create_time")
    void staleQueryMustJudgeByUpdateTime() throws NoSuchMethodException {
        String sql = selectSql();

        assertTrue(sql.contains("update_time"),
                "兜底扫描必须按 update_time 判断陈旧度，否则重投后同一条消息会被立即重复命中");
        assertFalse(sql.contains("create_time"),
                "兜底扫描不能再出现 create_time：它在消息生命周期内永不变化，会导致 25 秒内连投 5 次被判死");
    }

    @Test
    @DisplayName("P1-2：兜底扫描的三个必要判据不能丢（业务类型 / 已投递状态 / 无消费记录）")
    void staleQueryMustKeepItsGuardConditions() throws NoSuchMethodException {
        String sql = selectSql();

        assertTrue(sql.contains("status = 1"), "必须只捞「已投递」状态，不能把待投递/已消费/判死的消息卷进来");
        assertTrue(sql.contains("mq_consume_log"), "必须反连消费日志表，否则消费成功的消息也会被反复重投");
        assertTrue(sql.contains("message_id IS NULL"), "必须有消费日志缺失的反连接条件");
    }

    @Test
    @DisplayName("P1-2：重投时必须显式刷新 update_time，保证新一轮观察窗从头计时")
    void resetToPendingMustRefreshUpdateTime() throws NoSuchMethodException {
        Method method = Arrays.stream(LocalMessageMapper.class.getDeclaredMethods())
                .filter(candidate -> "resetToPending".equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 resetToPending"));
        Update update = method.getAnnotation(Update.class);
        assertNotNull(update, "resetToPending 必须是 @Update 注解 SQL");
        String sql = String.join(" ", update.value());

        assertTrue(sql.contains("update_time = NOW()"),
                "重投语句必须显式刷新 update_time；否则下一轮扫描仍按旧时间命中，兜底会退化成热循环");
        assertTrue(sql.contains("LEAST(retry_count + 1"),
                "retry_count 必须封顶，避免超过调度器查询上界后消息永久卡死");
    }

    private String selectSql() throws NoSuchMethodException {
        Method method = LocalMessageMapper.class.getMethod(
                STALE_QUERY_METHOD, LocalDateTime.class, String.class, int.class);
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select, "兜底扫描必须是 @Select 注解 SQL");
        return String.join(" ", select.value());
    }
}
