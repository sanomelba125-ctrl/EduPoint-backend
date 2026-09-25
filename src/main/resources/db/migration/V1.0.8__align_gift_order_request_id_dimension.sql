-- ===================================================================
-- 阶段二 P2-2: 礼品兑换订单统一幂等维度治理
-- 目的: 将数据库唯一索引 uk_request_id 升级为 (student_id, request_id) 联合唯一索引 uk_stu_request_id，
--       彻底对齐 Redis 幂等隔离维度 (gift:exchange:idem:{studentId}:{requestId}) 与 DB 唯一索引维度
-- ===================================================================

ALTER TABLE `student_gift`
    DROP INDEX `uk_request_id`,
    ADD UNIQUE KEY `uk_stu_request_id` (`student_id`, `request_id`);
