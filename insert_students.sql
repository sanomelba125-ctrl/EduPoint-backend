-- -------------------------------------------------------------------
-- 批量生成演示学生用户并写入 MySQL (默认密码 123456)
-- -------------------------------------------------------------------
SET NAMES utf8mb4;

INSERT INTO `student` (`student_no`, `password`, `name`, `gender`, `avatar`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`)
VALUES 
('2023010101', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '林雨桐', 2, 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', '外国语学院', '商务英语', '商英2301班', '13911110007', 260, 260, 1),
('2023010202', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '周子涵', 1, 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', '人工智能学院', '人工智能', '智科2302班', '13911110008', 380, 310, 1),
('2023010303', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '李嘉欣', 2, 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', '经济管理学院', '金融科技', '金科2301班', '13911110009', 190, 140, 1),
('2024010105', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '刘思源', 1, 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', '信息工程学院', '网络空间安全', '网安2401班', '13911110010', 150, 150, 1),
('2024010206', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '黄小雅', 2, 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', '艺术设计学院', '视觉传达设计', '视传2402班', '13911110011', 80, 80, 1)
ON DUPLICATE KEY UPDATE 
    `name` = VALUES(`name`),
    `college` = VALUES(`college`),
    `major` = VALUES(`major`),
    `class_name` = VALUES(`class_name`),
    `total_points` = VALUES(`total_points`),
    `current_points` = VALUES(`current_points`),
    `phone` = VALUES(`phone`),
    `status` = 1;

-- 为新学生补充积分流水台账 (关联学生表主键)
INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 1, 260, 0, 260, '参与活动打卡奖励【跨文化国际学术交流沙龙】', '2026-03-02 14:00:00'
FROM `student` s WHERE s.student_no = '2023010101'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '参与活动打卡奖励【跨文化国际学术交流沙龙】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 1, 380, 0, 380, '参与活动打卡奖励【全国大学生AI算法挑战赛校内选拔】', '2026-03-03 10:00:00'
FROM `student` s WHERE s.student_no = '2023010202'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '参与活动打卡奖励【全国大学生AI算法挑战赛校内选拔】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 2, -70, 380, 310, '兑换积分商城礼品【定制校园环保帆布袋套装】', '2026-03-06 15:30:00'
FROM `student` s WHERE s.student_no = '2023010202'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '兑换积分商城礼品【定制校园环保帆布袋套装】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 1, 190, 0, 190, '参与活动打卡奖励【金融科技前沿与量化投资专题讲座】', '2026-03-04 16:20:00'
FROM `student` s WHERE s.student_no = '2023010303'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '参与活动打卡奖励【金融科技前沿与量化投资专题讲座】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 2, -50, 190, 140, '兑换积分商城礼品【文创精装记事本】', '2026-03-07 11:10:00'
FROM `student` s WHERE s.student_no = '2023010303'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '兑换积分商城礼品【文创精装记事本】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 1, 150, 0, 150, '参与活动打卡奖励【校园网络安全攻防演练与实践】', '2026-03-05 09:30:00'
FROM `student` s WHERE s.student_no = '2024010105'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '参与活动打卡奖励【校园网络安全攻防演练与实践】');

INSERT INTO `student_point_record` (`student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `create_time`)
SELECT s.id, 1, 80, 0, 80, '参与活动打卡奖励【文创艺术设计与创意手作工作坊】', '2026-03-08 14:15:00'
FROM `student` s WHERE s.student_no = '2024010206'
AND NOT EXISTS (SELECT 1 FROM `student_point_record` r WHERE r.student_id = s.id AND r.description = '参与活动打卡奖励【文创艺术设计与创意手作工作坊】');
