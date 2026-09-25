-- ===================================================================
-- 阶段二 P2-1: 积分明细流水表添加业务幂等键 biz_no 与唯一索引 uk_biz_type
-- 目的: 杜绝积分重复记账，为 MQ 异步消费幂等 (P1-4) 提供物理层最终一致性屏障
-- ===================================================================

-- 1. 新增 biz_no 业务幂等单据号字段（先设为 NULL 避免已有数据时直接设 NOT NULL 报错）
ALTER TABLE `student_point_record`
    ADD COLUMN `biz_no` VARCHAR(64) DEFAULT NULL COMMENT '业务幂等单据号(订单号/打卡参与记录号/调账审批号)' AFTER `ref_id`;

-- 2. 为历史遗留数据补充唯一 biz_no（基于历史主键 id 规整，保证已有记录唯一不冲突）
UPDATE `student_point_record`
SET `biz_no` = CONCAT('LEGACY-', `id`)
WHERE `biz_no` IS NULL OR `biz_no` = '';

-- 3. 将 biz_no 字段约束正式更新为 NOT NULL
ALTER TABLE `student_point_record`
    MODIFY COLUMN `biz_no` VARCHAR(64) NOT NULL COMMENT '业务幂等单据号(订单号/打卡参与记录号/调账审批号)';

-- 4. 添加 uk_biz_type (biz_no, type) 联合唯一索引
ALTER TABLE `student_point_record`
    ADD UNIQUE KEY `uk_biz_type` (`biz_no`, `type`);
