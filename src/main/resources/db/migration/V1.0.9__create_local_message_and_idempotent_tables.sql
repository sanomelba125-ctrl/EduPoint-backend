-- ===================================================================
-- 第六阶段：高并发秒杀与分布式事务最终一致性数据表支持 (P1-3 & P1-4)
-- 1. local_message: 事务性发件箱 (Transactional Outbox) 本地消息表
-- 2. mq_consume_log: 消费端业务强幂等防重表
-- ===================================================================

CREATE TABLE IF NOT EXISTS `local_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    `message_id` VARCHAR(64) NOT NULL COMMENT '全局唯一消息ID',
    `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型(GIFT_ORDER_CREATE, DELAY_CLOSE, etc.)',
    `biz_id` VARCHAR(64) NOT NULL COMMENT '业务唯一单据号/请求标识',
    `payload` TEXT NOT NULL COMMENT '不可变消息体JSON',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-待投递 1-已投递 2-已确认处理完成 3-重试超限失败',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '投递重试次数',
    `next_retry_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次重试补偿时间',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '全链路链路追踪号',
    `fail_reason` VARCHAR(512) DEFAULT NULL COMMENT '失败归因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_message_id` (`message_id`),
    INDEX `idx_biz` (`biz_type`, `biz_id`),
    INDEX `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务性发件箱本地消息表';

CREATE TABLE IF NOT EXISTS `mq_consume_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    `message_id` VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    `consumer_name` VARCHAR(64) NOT NULL COMMENT '消费者名称/集群标识',
    `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型',
    `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '关联业务单据号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-消费成功 2-业务已拒绝',
    `consume_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费入库时间',
    UNIQUE KEY `uk_msg_consumer` (`message_id`, `consumer_name`),
    INDEX `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息队列消费幂等防重表';
