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
