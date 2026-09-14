-- 礼品兑换订单幂等支撑：客户端请求幂等标识 + 唯一索引
-- 说明：
--   1. request_id 由移动端在「一次兑换意图」内保持不变（弱网重试复用同一标识），成功后重置；
--   2. 唯一索引是幂等的最后一道防线，与 Redis SETNX 占位形成双保险；
--   3. 历史订单该列为 NULL，MySQL 唯一索引允许多个 NULL，不影响存量数据。
ALTER TABLE `student_gift`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '客户端兑换请求幂等标识(UUID，防重复落单)' AFTER `points_cost`,
    ADD UNIQUE KEY `uk_request_id` (`request_id`);
