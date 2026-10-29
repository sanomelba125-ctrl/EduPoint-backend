package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表数据访问层 (Transactional Outbox Mapper)
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 查询待重试的消息列表 (状态为 0 且重试时间到达，包含已达到最大重试次数的记录，
     * 由调度器负责将其判死并补偿，否则第 maxRetries 次失败后记录将永远无法进入判死分支)
     */
    @Select("SELECT * FROM local_message " +
            "WHERE status = 0 AND next_retry_time <= #{now} AND retry_count <= #{maxRetries} " +
            "ORDER BY next_retry_time ASC LIMIT #{limit}")
    List<LocalMessage> selectPendingMessages(@Param("now") LocalDateTime now,
                                             @Param("maxRetries") int maxRetries,
                                             @Param("limit") int limit);

    /**
     * 查询「在途」的秒杀下单消息（尚未确认完成），供库存对账扣减在途预扣量。
     *
     * <p>在途 = 待投递(0) + 已投递待确认(1)。已确认完成(2) 的库存已由消费落库扣减，
     * 重试超限失败(3) 的库存已由 {@code OrderCreateConsumer} 反向补偿回补，
     * 两者都不应再计入在途，否则会重复扣减导致对账偏低。
     */
    @Select("SELECT message_id, biz_type, biz_id, payload, status FROM local_message " +
            "WHERE biz_type = 'GIFT_ORDER_CREATE' AND status IN (0, 1)")
    List<LocalMessage> selectInflightOrderMessages();

    /**
     * 更新状态为已投递
     */
    @Update("UPDATE local_message SET status = 1 WHERE message_id = #{messageId} AND status = 0")
    int markAsPublished(@Param("messageId") String messageId);

    /**
     * 更新状态为已确认消费
     */
    @Update("UPDATE local_message SET status = 2 WHERE message_id = #{messageId}")
    int markAsConsumed(@Param("messageId") String messageId);

    /**
     * 更新状态为失败并记录原因
     */
    @Update("UPDATE local_message SET status = #{status}, fail_reason = #{reason} WHERE message_id = #{messageId}")
    int markAsFailed(@Param("messageId") String messageId,
                     @Param("status") int status,
                     @Param("reason") String reason);

    /**
     * 累加重试次数并设置下一次重试时间
     */
    @Update("UPDATE local_message SET retry_count = retry_count + 1, next_retry_time = #{nextRetryTime}, fail_reason = #{reason} " +
            "WHERE message_id = #{messageId} AND status = 0")
    int incrementRetry(@Param("messageId") String messageId,
                       @Param("nextRetryTime") LocalDateTime nextRetryTime,
                       @Param("reason") String reason);

    /**
     * 把消息退回「待投递」并累加重试次数。
     *
     * <p>用于发布侧可靠性失败：Broker NACK、消息不可路由（basic.return）、
     * 或长期停留在「已投递」却查不到任何消费记录。只允许从 0/1 回退，
     * 已确认消费(2) 与已判死(3) 是终态，不能被晚到的确认回调改写。
     *
     * <p>重试次数用 {@code LEAST(...)} 封顶：若允许无限累加，一旦超过调度器的
     * {@code retry_count <= maxRetries} 查询上界，消息就再也不会被扫描到，
     * 既不会重投也不会判死补偿，比不重投更糟。
     */
    @Update("UPDATE local_message SET status = 0, retry_count = LEAST(retry_count + 1, #{maxRetries}), " +
            "next_retry_time = #{nextRetryTime}, fail_reason = #{reason}, update_time = NOW() " +
            "WHERE message_id = #{messageId} AND status IN (0, 1)")
    int resetToPending(@Param("messageId") String messageId,
                       @Param("nextRetryTime") LocalDateTime nextRetryTime,
                       @Param("reason") String reason,
                       @Param("maxRetries") int maxRetries);

    /**
     * 查询「已投递但长时间没有消费记录」的秒杀下单消息。
     *
     * <p>发布确认只能证明 Broker 收到了消息，不能证明消息最终被消费：
     * 队列在写入磁盘前宕机、消息在镜像切换中丢失、消费端长时间不启动，
     * 都会让消息停在 status=1 而永不重投。这里以「<b>最近一次状态变更</b>超过阈值 + 消费日志缺失」
     * 作为兜底判据把它们捞出来重投，消费端幂等表保证重复投递不会重复落单。</p>
     *
     * <p><b>为什么用 {@code update_time} 而不是 {@code create_time}（2026-10-29 修复）：</b>
     * {@code create_time} 一旦写入永不变化，而重投链路会把它一路刷新——
     * {@code resetToPending} 显式写 {@code update_time = NOW()}，{@code markAsPublished}
     * 把 status 0→1 时列上的 {@code ON UPDATE CURRENT_TIMESTAMP} 也会自动刷新。
     * 旧写法按 {@code create_time} 判断，一条创建于 1 小时前的消息被捞回重投、5 秒后再次变成
     * status=1（此时消费端可能还没写消费日志），下一轮扫描立刻又命中，25 秒内连投 5 次
     * 直接把 {@code retry_count} 顶到上限判死并触发库存回补。
     * 换成 {@code update_time} 后，每次重投都会重置 10 分钟观察窗，判死需要 5 个完整周期。</p>
     */
    @Select("SELECT m.* FROM local_message m " +
            "LEFT JOIN mq_consume_log c ON c.message_id = m.message_id AND c.consumer_name = #{consumerName} " +
            "WHERE m.biz_type = 'GIFT_ORDER_CREATE' AND m.status = 1 " +
            "AND m.update_time <= #{staleBefore} AND c.message_id IS NULL " +
            "ORDER BY m.update_time ASC LIMIT #{limit}")
    List<LocalMessage> selectStalePublishedMessages(@Param("staleBefore") LocalDateTime staleBefore,
                                                    @Param("consumerName") String consumerName,
                                                    @Param("limit") int limit);

    /**
     * 修复「消费端已有终态，但本地消息状态没来得及更新」的窗口。
     * 例如订单已提交、消费日志 status=1 已写入，但 markConsumed 失败；
     * 下一轮调度通过消费日志把本地消息补齐为完成/失败，避免永久计入在途预扣。
     */
    @Update("UPDATE local_message m " +
            "JOIN mq_consume_log c ON c.message_id = m.message_id " +
            "AND c.consumer_name = 'OrderCreateConsumer' " +
            "SET m.status = CASE WHEN c.status = 1 THEN 2 WHEN c.status = 2 THEN 3 ELSE m.status END, " +
            "    m.fail_reason = CASE WHEN c.status = 2 THEN COALESCE(m.fail_reason, '消费端已判定业务失败') ELSE m.fail_reason END " +
            "WHERE m.status IN (0, 1) AND c.status IN (1, 2)")
    int repairTerminalStatusFromConsumeLog();

    /**
     * 统计当前待投递积压消息总数 (status = 0)
     */
    @Select("SELECT COUNT(*) FROM local_message WHERE status = 0")
    long countPendingBacklog();
}
