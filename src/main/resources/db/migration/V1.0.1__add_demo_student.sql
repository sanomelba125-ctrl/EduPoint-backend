-- ===================================================================
-- V1.0.1: 补齐移动端核心演示学生账户 (张晓明 2023010401) 与对应学分流水
-- ===================================================================

-- 1. 注入张晓明学生档案 (初始密码 123456，总积分 420 居全校排行榜第一位)
INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`)
VALUES 
(6, '2023010401', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '张晓明', 1, '信息工程学院', '软件工程', '软工2301班', '13911110006', 420, 350, 1)
ON DUPLICATE KEY UPDATE 
    `name` = VALUES(`name`),
    `college` = VALUES(`college`),
    `major` = VALUES(`major`),
    `class_name` = VALUES(`class_name`),
    `total_points` = VALUES(`total_points`),
    `current_points` = VALUES(`current_points`),
    `status` = 1;

-- 2. 补齐对应积分变动流水台账 (确保 300 + 120 - 70 = 350，对账一致)
INSERT INTO `student_point_record` (`id`, `student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `ref_id`, `operator_id`, `create_time`)
VALUES 
(6, 6, 1, 300, 0, 300, '参与活动打卡奖励【“青年AI与新质生产力”前沿学术讲座】', 2, NULL, '2026-03-01 10:30:00'),
(7, 6, 1, 120, 300, 420, '参与活动打卡奖励【春季校园科技创新与程序设计大赛】', 3, NULL, '2026-03-03 16:45:00'),
(8, 6, 2, -70, 420, 350, '兑换积分商城礼品【定制校园环保帆布袋套装】', 1, NULL, '2026-03-05 14:20:00')
ON DUPLICATE KEY UPDATE 
    `change_points` = VALUES(`change_points`),
    `before_points` = VALUES(`before_points`),
    `after_points` = VALUES(`after_points`);
