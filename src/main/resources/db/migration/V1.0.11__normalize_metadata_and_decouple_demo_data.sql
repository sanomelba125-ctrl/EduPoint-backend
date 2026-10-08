-- ===================================================================
-- V1.0.11: 数据库版本演进规范治理与演示数据解耦归正
-- 
-- 【企业级 Flyway 变更原则 (Production Migration Principles)】:
-- 1. 已执行的历史迁移脚本 (V1.0.0 ~ V1.0.10) 严格不可篡改 (Immutability)，保证校验和 Checksum 绝对一致；
-- 2. DDL 结构演进与 DML 演示数据解耦：全量演示业务数据已剥离至 backend/src/main/resources/data/demo-data.sql；
-- 3. 本脚本作为历史脏数据收口迁移：安全清理前期调试残留的临时脏测试数据 (如 id=114514 等)，
--    将系统基础元数据规范化，确立生产就绪基准 (Production-Ready Baseline)。
-- ===================================================================

SET NAMES utf8mb4;

-- 1. 安全清理历史遗留的非标准测试活动及关联打卡记录
DELETE FROM `student_activity` WHERE `activity_id` IN (114514, 114515, 114516);
DELETE FROM `certificate` WHERE `activity_id` IN (114514, 114515, 114516);
DELETE FROM `activity` WHERE `id` IN (114514, 114515, 114516);

-- 2. 清理指向非标准测试活动的临时通知
DELETE FROM `sys_notice` WHERE `content` LIKE '%面向ai编程%' OR `content` LIKE '%检查并发用例%';

-- 3. 规范核心基础管理员元数据 (确保 admin / counselor 账号常驻)
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`, `phone`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'admin','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','超级管理员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','SUPER_ADMIN','13800000001',1,NOW(),NOW(),0),
(2,'counselor','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','李辅导员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','COUNSELOR','13800000002',1,NOW(),NOW(),0)
ON DUPLICATE KEY UPDATE
    `username` = VALUES(`username`),
    `password` = VALUES(`password`),
    `nickname` = VALUES(`nickname`),
    `role` = VALUES(`role`),
    `status` = VALUES(`status`),
    `deleted` = 0;

-- 4. 确保主线标杆活动 (ID 1~4) 状态与时间处于健康有效周期
UPDATE `activity` 
SET `start_time` = '2026-03-01 08:00:00', 
    `end_time` = '2026-12-31 23:59:59',
    `status` = 1,
    `deleted` = 0
WHERE `id` IN (1, 2, 3, 4);
