-- ===================================================================
-- 校园活动积分平台 (EduPoint) - 生产级数据库初始化脚本
-- 严格依据《校园活动积分平台需求说明.md》设计
-- 适用数据库: MySQL 8.0+
-- 字符集: utf8mb4 / 排序规则: utf8mb4_general_ci
-- ===================================================================

CREATE DATABASE IF NOT EXISTS `campus_activity` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `campus_activity`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------------
-- 1. 系统管理员/后台用户表 (sys_user)
-- 对应需求: 2.1 管理员角色 / 3.1 登录认证 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理员主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号(唯一标识)',
    `password` VARCHAR(100) NOT NULL COMMENT '登录密码(BCrypt加盐密文)',
    `nickname` VARCHAR(50) NOT NULL COMMENT '姓名/显示昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '用户头像URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ADMIN' COMMENT '角色权限: SUPER_ADMIN-超级管理员, ADMIN-普通管理员, COUNSELOR-辅导员',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系手机号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态: 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户与管理员表';

-- 初始系统管理员种子数据 (初始密码均为 123 的 BCrypt 哈希值)
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`, `phone`, `status`)
VALUES 
(1, 'admin', '$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO', '超级管理员', 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png', 'SUPER_ADMIN', '13800000001', 1),
(2, 'counselor', '$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO', '李辅导员', 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png', 'COUNSELOR', '13800000002', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- -------------------------------------------------------------------
-- 2. 学生档案信息与核心账户表 (student)
-- 对应需求: 2.2 学生角色 / 3.1 登录认证 / 3.6 积分管理 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生主键ID',
    `student_no` VARCHAR(30) NOT NULL COMMENT '学号(唯一业务标识)',
    `password` VARCHAR(100) NOT NULL DEFAULT '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru' COMMENT '移动端登录密码(初始默认 123456 的 BCrypt 哈希值)',
    `name` VARCHAR(50) NOT NULL COMMENT '学生真实姓名',
    `gender` TINYINT NOT NULL DEFAULT 1 COMMENT '性别: 1-男 2-女',
    `avatar` VARCHAR(255) DEFAULT 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png' COMMENT '个人头像URL',
    `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序OpenID(用于支持微信一键登录与绑定)',
    `college` VARCHAR(100) DEFAULT NULL COMMENT '所属学院',
    `major` VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '行政班级',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系手机号',
    `total_points` INT NOT NULL DEFAULT 0 COMMENT '历史累计获得总积分(用于排行榜与成就计算)',
    `current_points` INT NOT NULL DEFAULT 0 COMMENT '当前可用积分余额(兑换礼品实时扣减，不得小于0)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '学生账户状态: 0-冻结/休学 1-正常在籍',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '档案创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '档案更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`),
    KEY `idx_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生档案与积分主账户表';

-- 初始学生种子数据 (密码均为 123456 的 BCrypt 哈希值)
INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`)
VALUES 
(1, '20220101', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '张伟', 1, '信息工程学院', '计算机科学与技术', '计科2201班', '13911110001', 320, 180, 1),
(2, '20220102', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '王芳', 2, '商学院', '电子商务', '电商2202班', '13911110002', 280, 280, 1),
(3, '20220103', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '陈明', 1, '机械工程学院', '智能制造工程', '智造2201班', '13911110003', 210, 110, 1),
(4, '20220104', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '赵丽', 2, '人文学院', '汉语言文学', '中文2203班', '13911110004', 150, 150, 1),
(5, '20220105', '$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru', '孙浩', 1, '信息工程学院', '软件工程', '软工2201班', '13911110005', 90, 40, 1)
ON DUPLICATE KEY UPDATE `student_no` = VALUES(`student_no`);

-- -------------------------------------------------------------------
-- 3. 校园活动表 (activity)
-- 对应需求: 3.2 活动管理 / 3.14 地图与定位 / 3.16 电子证书 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动主键ID',
    `title` VARCHAR(100) NOT NULL COMMENT '活动标题(必填)',
    `description` TEXT DEFAULT NULL COMMENT '活动详情说明/富文本',
    `rule_description` TEXT DEFAULT NULL COMMENT '积分与打卡规则说明',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '活动宣传封面图URL',
    `points` INT NOT NULL DEFAULT 10 COMMENT '打卡完成奖励积分(正整数)',
    `start_time` DATETIME NOT NULL COMMENT '活动开始/报名打卡起始时间',
    `end_time` DATETIME NOT NULL COMMENT '活动结束/打卡截止时间',
    `registration_limit` INT NOT NULL DEFAULT 0 COMMENT '正式报名人数上限(0表示不限制人数)',
    `participant_count` INT NOT NULL DEFAULT 0 COMMENT '已成功报名人数',
    `waitlist_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用候补队列: 0-否 1-是',
    `location_name` VARCHAR(150) DEFAULT NULL COMMENT '地点名称(如“西区田径场”)',
    `location_address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址说明',
    `longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '经度(-180~180)',
    `latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度(-90~90)',
    `location_radius` INT NOT NULL DEFAULT 300 COMMENT '允许打卡签到半径(米，默认300米)',
    `location_check_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用定位范围签到: 0-否 1-是',
    `certificate_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否颁发电子荣誉证书: 0-否 1-是',
    `certificate_title` VARCHAR(100) DEFAULT NULL COMMENT '证书名称(如“优秀志愿者荣誉证书”)',
    `certificate_issuer` VARCHAR(100) DEFAULT NULL COMMENT '证书签发单位(如“校团委/学生工作部”)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态: 0-未开始 1-进行中 2-已截止/已结束 3-已关闭/归档',
    `created_by` BIGINT DEFAULT NULL COMMENT '发布管理员ID(关联 sys_user.id)',
    `updated_by` BIGINT DEFAULT NULL COMMENT '最后修改管理员ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动信息表';

-- 初始活动种子数据
INSERT INTO `activity` (`id`, `title`, `description`, `rule_description`, `cover_image`, `points`, `start_time`, `end_time`, `registration_limit`, `participant_count`, `waitlist_enabled`, `location_name`, `location_address`, `longitude`, `latitude`, `location_radius`, `location_check_enabled`, `certificate_enabled`, `certificate_title`, `certificate_issuer`, `status`, `created_by`)
VALUES 
(1, '2026春季校园晨跑健康打卡', '坚持早起锻炼，强健体魄！在西区田径场完成晨跑签到即可累计积分。', '每天早上 06:30 - 08:30 之间到达田径场，出示动态二维码并提交定位即可打卡。', 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500', 15, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), 100, 24, 1, '西区田径操场', '湖北省武汉市洪山区大学园路1号西区体育场', 114.362145, 30.523412, 500, 1, 0, NULL, NULL, 1, 1),
(2, '“青年AI与新质生产力”前沿学术讲座', '邀请行业知名技术专家进校分享大模型与前沿技术实战，拓宽学术视野。', '讲座入场时扫描会场动态二维码完成打卡，全程参与可获得电子学术结业证书。', 'https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=500', 30, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), 50, 48, 1, '图书馆一楼大报告厅', '图书馆主楼A区一层东侧报告厅', 114.364200, 30.524100, 200, 1, 1, 'AI前沿学术讲座参与证书', '校科技创新与创业协会', 1, 1),
(3, '雷锋月：校园公共绿地清洁志愿服务', '践行雷锋精神，共同建设美丽校园，开展草坪修整与卫生清洁。', '在学生活动中心集合签到，统一发放清洁工具，活动结束后颁发志愿服务证书。', 'https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500', 25, DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), 30, 0, 1, '学生活动中心门前广场', '学生活动中心南门广场旗杆下', 114.361500, 30.522800, 300, 0, 1, '校园环保志愿服务荣誉证书', '校青年志愿者协会', 0, 1),
(4, '读书月：经典品读与思辨沙龙 (往期归档)', '阅读经典名著，分享读书感悟与体会。', '现场签到并参与讨论。', 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500', 10, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 40, 40, 0, '博学楼201教室', '博学楼二楼西侧多媒体教室', 114.363000, 30.523000, 300, 0, 0, NULL, NULL, 2, 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- -------------------------------------------------------------------
-- 4. 学生活动参与/报名/打卡记录表 (student_activity)
-- 对应需求: 3.3 活动报名 / 3.4 活动打卡 / 3.5 活动记录 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_activity`;
CREATE TABLE `student_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '参与记录主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID(关联 student.id)',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID(关联 activity.id)',
    `signup_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名成功时间',
    `checkin_time` DATETIME DEFAULT NULL COMMENT '实际打卡完成时间',
    `checkin_latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '学生实际打卡位置纬度',
    `checkin_longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '学生实际打卡位置经度',
    `checkin_distance` INT DEFAULT NULL COMMENT '打卡点距活动目标中心距离(米)',
    `qr_nonce_used` VARCHAR(64) DEFAULT NULL COMMENT '打卡核销使用的动态二维码nonce',
    `points_awarded` INT NOT NULL DEFAULT 0 COMMENT '实际获得的奖励积分',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '参与状态: 1-已报名(待打卡) 2-已完成(打卡成功发分) 3-未完成或已失效',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注/打卡审核反馈',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stu_act` (`student_id`, `activity_id`) COMMENT '一学生一活动唯一约束(防止重复报名与重复打卡)',
    KEY `idx_act_status` (`activity_id`, `status`),
    KEY `idx_stu_status` (`student_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生活动参与记录表';

-- 初始参与记录种子数据
INSERT INTO `student_activity` (`id`, `student_id`, `activity_id`, `signup_time`, `checkin_time`, `checkin_latitude`, `checkin_longitude`, `checkin_distance`, `qr_nonce_used`, `points_awarded`, `status`, `remark`)
VALUES 
(1, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 30.523420, 114.362150, 8, 'NONCE_20260301_晨跑_001', 15, 2, 'GPS范围校验通过，打卡有效'),
(2, 2, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 30.523410, 114.362140, 6, 'NONCE_20260301_晨跑_002', 15, 2, 'GPS范围校验通过，打卡有效'),
(3, 1, 2, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), 30.524105, 114.364210, 12, 'NONCE_20260301_讲座_001', 30, 2, '现场扫码打卡成功'),
(4, 3, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR), NULL, NULL, NULL, NULL, NULL, 0, 1, '已成功报名，待打卡'),
(5, 4, 1, DATE_SUB(NOW(), INTERVAL 4 HOUR), NULL, NULL, NULL, NULL, NULL, 0, 1, '已成功报名，待打卡')
ON DUPLICATE KEY UPDATE `points_awarded` = VALUES(`points_awarded`);

-- -------------------------------------------------------------------
-- 5. 活动报名排队候补队列表 (activity_waitlist)
-- 对应需求: 3.3 活动报名(候补机制) / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `activity_waitlist`;
CREATE TABLE `activity_waitlist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '候补记录ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID(关联 activity.id)',
    `student_id` BIGINT NOT NULL COMMENT '候补学生ID(关联 student.id)',
    `queue_number` INT NOT NULL COMMENT '候补排队序号(按报名时间自增递增)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '候补状态: 1-候补中 2-已递补转正 3-主动取消 4-活动结束已失效',
    `wait_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '进入候补队列时间',
    `promoted_time` DATETIME DEFAULT NULL COMMENT '递补成功转正时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_act_queue` (`activity_id`, `status`, `queue_number`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名候补排队记录表';

-- 初始候补队列模拟数据
INSERT INTO `activity_waitlist` (`id`, `activity_id`, `student_id`, `queue_number`, `status`, `wait_time`, `promoted_time`)
VALUES 
(1, 2, 5, 1, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR), NULL)
ON DUPLICATE KEY UPDATE `queue_number` = VALUES(`queue_number`);

-- -------------------------------------------------------------------
-- 6. 动态打卡二维码核销防伪记录表 (activity_checkin_code)
-- 对应需求: 3.4 活动打卡(防作弊动态二维码) / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `activity_checkin_code`;
CREATE TABLE `activity_checkin_code` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID(关联 activity.id)',
    `nonce` VARCHAR(64) NOT NULL COMMENT '防伪随机串(全局唯一不可重放)',
    `sign` VARCHAR(128) NOT NULL COMMENT '服务端签名参数',
    `expire_time` DATETIME NOT NULL COMMENT '二维码有效截止时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '使用状态: 0-有效待核销 1-已被使用打卡 2-已失效',
    `used_by_student_id` BIGINT DEFAULT NULL COMMENT '使用扫码打卡的学生ID',
    `used_time` DATETIME DEFAULT NULL COMMENT '扫码核销时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nonce` (`nonce`),
    KEY `idx_act_expire` (`activity_id`, `expire_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动动态打卡二维码核销防伪表';

-- 初始动态二维码模拟数据
INSERT INTO `activity_checkin_code` (`id`, `activity_id`, `nonce`, `sign`, `expire_time`, `status`, `used_by_student_id`, `used_time`)
VALUES 
(1, 1, 'NONCE_20260301_晨跑_001', 'SIGN_晨跑_ABC1234567890', NOW(), 1, 1, NOW()),
(2, 1, 'NONCE_20260301_晨跑_002', 'SIGN_晨跑_DEF1234567890', NOW(), 1, 2, NOW()),
(3, 2, 'NONCE_20260301_讲座_001', 'SIGN_讲座_GHI1234567890', NOW(), 1, 1, NOW()),
(4, 2, 'NONCE_CURRENT_ACTIVE_TOKEN', 'SIGN_讲座_ACTIVE_SECRET', DATE_ADD(NOW(), INTERVAL 30 MINUTE), 0, NULL, NULL)
ON DUPLICATE KEY UPDATE `nonce` = VALUES(`nonce`);

-- -------------------------------------------------------------------
-- 7. 学生积分明细台账流水表 (student_point_record)
-- 对应需求: 3.6 积分管理 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_point_record`;
CREATE TABLE `student_point_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID(关联 student.id)',
    `type` TINYINT NOT NULL COMMENT '变动业务类型: 1-活动打卡奖励(+) 2-商城兑换消耗(-) 3-管理员手动调整(±) 4-订单取消返还(+)',
    `change_points` INT NOT NULL COMMENT '变动积分值(正数增加，负数扣除)',
    `before_points` INT NOT NULL COMMENT '变动前可用积分余额',
    `after_points` INT NOT NULL COMMENT '变动后可用积分余额',
    `description` VARCHAR(255) NOT NULL COMMENT '积分变动业务明细说明',
    `ref_id` BIGINT DEFAULT NULL COMMENT '关联业务ID(如 student_activity.id 或 student_gift.id)',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID(关联 sys_user.id，系统自动触发则为0或NULL)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水记账时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_stu_time` (`student_id`, `create_time`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生积分明细台账流水表';

-- 初始积分明细流水种子数据
INSERT INTO `student_point_record` (`id`, `student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `ref_id`, `operator_id`)
VALUES 
(1, 1, 1, 15, 0, 15, '参与活动打卡奖励【2026春季校园晨跑健康打卡】', 1, NULL),
(2, 1, 1, 30, 15, 45, '参与活动打卡奖励【“青年AI与新质生产力”前沿学术讲座】', 3, NULL),
(3, 1, 2, -50, 230, 180, '兑换积分商城礼品【定制校园帆布袋】', 1, NULL),
(4, 2, 1, 15, 265, 280, '参与活动打卡奖励【2026春季校园晨跑健康打卡】', 2, NULL),
(5, 3, 3, 50, 60, 110, '管理员考勤特别嘉奖加分', NULL, 1)
ON DUPLICATE KEY UPDATE `change_points` = VALUES(`change_points`);

-- -------------------------------------------------------------------
-- 8. 积分商城礼品库表 (gift)
-- 对应需求: 3.8 礼物管理 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `gift`;
CREATE TABLE `gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '礼品主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '礼品名称(必填)',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '礼品规格/详细描述',
    `image` VARCHAR(255) DEFAULT NULL COMMENT '礼品展示主图URL',
    `points_required` INT NOT NULL DEFAULT 50 COMMENT '兑换所需积分单价(正整数)',
    `total_stock` INT NOT NULL DEFAULT 0 COMMENT '礼品入库总库存',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '当前可用库存(兑换实时扣减，>=0)',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(高并发防超卖)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态: 0-已下架/停用 1-上架兑换中',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建管理员ID',
    `updated_by` BIGINT DEFAULT NULL COMMENT '修改管理员ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城礼品库表';

-- 初始礼品种子数据
INSERT INTO `gift` (`id`, `name`, `description`, `image`, `points_required`, `total_stock`, `stock`, `version`, `status`, `created_by`)
VALUES 
(1, '校园定制环保帆布袋', '加厚纯棉帆布，印有校徽与校训刺绣标志，容量大结实耐用。', 'https://images.unsplash.com/photo-1544816155-12df9643f363?w=500', 50, 100, 95, 1, 1, 1),
(2, '高档磨砂双层保温杯', '316L食品级医用不锈钢内胆，长效保温保冷，激光镭雕定制。', 'https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=500', 120, 50, 48, 0, 1, 1),
(3, '运动智能手环 8 代', '全天候健康心率监测、血氧检测、50米专业防水与超长续航。', 'https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?w=500', 300, 20, 15, 0, 1, 1),
(4, '期末备考：图书馆考研VIP自习位月卡', '专属安静自习位，配备独享充电插座、护眼台灯与储物柜。', 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500', 200, 30, 20, 0, 1, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- -------------------------------------------------------------------
-- 9. 礼品兑换订单与发放流转表 (student_gift)
-- 对应需求: 3.9 礼物兑换 / 3.10 兑换订单管理 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_gift`;
CREATE TABLE `student_gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '兑换订单主键ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '业务订单编号(全局唯一防重复)',
    `student_id` BIGINT NOT NULL COMMENT '兑换学生ID(关联 student.id)',
    `gift_id` BIGINT NOT NULL COMMENT '兑换礼品ID(关联 gift.id)',
    `points_cost` INT NOT NULL COMMENT '实际扣减积分',
    `receiver_name` VARCHAR(50) NOT NULL COMMENT '收件人真实姓名',
    `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收件人联系电话',
    `receiver_address` VARCHAR(255) NOT NULL COMMENT '收货地址或学生宿舍信息',
    `pickup_code` VARCHAR(32) NOT NULL COMMENT '核销提货码(唯一凭据)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态: 1-待发放 2-已送达/已核销 3-已取消',
    `refund_points_status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单取消时积分退还状态: 0-未退/不退 1-已全额退还',
    `delivery_time` DATETIME DEFAULT NULL COMMENT '实际送达/核销发放时间',
    `cancel_time` DATETIME DEFAULT NULL COMMENT '订单取消时间',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT '取消原因说明',
    `operator_id` BIGINT DEFAULT NULL COMMENT '经办管理员ID(关联 sys_user.id，追溯责任人)',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '发放备注/签收记录',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单兑换时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_pickup_code` (`pickup_code`),
    KEY `idx_student_order` (`student_id`, `status`),
    KEY `idx_gift_id` (`gift_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生礼品兑换订单与发放流转表';

-- 初始兑换订单模拟数据
INSERT INTO `student_gift` (`id`, `order_no`, `student_id`, `gift_id`, `points_cost`, `receiver_name`, `receiver_phone`, `receiver_address`, `pickup_code`, `status`, `refund_points_status`, `delivery_time`, `operator_id`, `remark`)
VALUES 
(1, 'ORD202603010001', 1, 1, 50, '张伟', '13911110001', '南区学生公寓12栋302室', 'PU20260301001', 2, 0, NOW(), 1, '已于一站式学生事务中心窗口核销领取'),
(2, 'ORD202603010002', 2, 2, 120, '王芳', '13911110002', '东区研究生宿舍5栋101室', 'PU20260301002', 1, 0, NULL, NULL, '待宿管站统一配送')
ON DUPLICATE KEY UPDATE `order_no` = VALUES(`order_no`);

-- -------------------------------------------------------------------
-- 10. 系统业务通知记录表 (sys_notice)
-- 对应需求: 3.13 实时通知 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `receiver_type` TINYINT NOT NULL COMMENT '接收方类型: 1-管理端(管理员) 2-学生端(学生)',
    `receiver_id` BIGINT NOT NULL COMMENT '接收人ID(关联 sys_user.id 或 student.id, 0代表全员广播)',
    `type` VARCHAR(50) NOT NULL COMMENT '通知类型: new_activity-新活动, gift_exchange-礼物兑换, activity_card-活动打卡, gift_send-礼物送达, waitlist_promoted-候补递补',
    `title` VARCHAR(150) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知消息正文内容',
    `biz_id` BIGINT DEFAULT NULL COMMENT '关联业务单据ID(如 activity.id, student_gift.id)',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态: 0-未读 1-已读',
    `read_time` DATETIME DEFAULT NULL COMMENT '阅读确认时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_receiver_read` (`receiver_type`, `receiver_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统业务通知记录表';

-- 初始业务通知示例数据
INSERT INTO `sys_notice` (`id`, `receiver_type`, `receiver_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `create_time`)
VALUES 
(1, 2, 1, 'new_activity', '新活动发布提醒', '“2026春季校园晨跑健康打卡”已开放报名，快来参与获取积分吧！', 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 1, 1, 'gift_exchange', '学生礼品兑换申请待处理', '学生 张伟 申请兑换【校园定制环保帆布袋】，订单号: ORD202603010001', 1, 1, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(3, 2, 1, 'gift_send', '礼品已核销发放通知', '您兑换的【校园定制环保帆布袋】已成功核销发放，感谢您的积极参与！', 1, 0, NOW()),
(4, 2, 5, 'waitlist_promoted', '活动候补递补成功通知', '您候补报名的“青年AI与新质生产力”学术讲座已有正式名额空出，您已成功递补转正！', 2, 0, NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- -------------------------------------------------------------------
-- 11. 电子荣誉证书及验证表 (certificate)
-- 对应需求: 3.16 电子证书与验证 / 4. 数据需求
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `certificate`;
CREATE TABLE `certificate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '证书记录ID',
    `certificate_no` VARCHAR(64) NOT NULL COMMENT '全局唯一证书编号(不可预测防伪串)',
    `student_id` BIGINT NOT NULL COMMENT '获奖学生ID(关联 student.id)',
    `activity_id` BIGINT NOT NULL COMMENT '对应活动ID(关联 activity.id)',
    `title` VARCHAR(100) NOT NULL COMMENT '证书名称(如“青年AI前沿学术讲座结业证书”)',
    `issuer` VARCHAR(100) NOT NULL COMMENT '签发单位(如“校科技创新协会”)',
    `issue_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '证书签发时间',
    `file_url` VARCHAR(255) DEFAULT NULL COMMENT '证书渲染生成的图片/PDF文件地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '证书状态: 1-有效 2-已撤销 3-已过期',
    `revoke_reason` VARCHAR(255) DEFAULT NULL COMMENT '撤销原因说明',
    `revoked_by` BIGINT DEFAULT NULL COMMENT '撤销操作管理员ID',
    `revoked_time` DATETIME DEFAULT NULL COMMENT '撤销时间',
    `verify_count` INT NOT NULL DEFAULT 0 COMMENT '被扫码验真核验次数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cert_no` (`certificate_no`),
    UNIQUE KEY `uk_stu_act_cert` (`student_id`, `activity_id`) COMMENT '一学生一活动只能生成一张有效证书',
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子荣誉证书及验真表';

-- 初始证书种子数据
INSERT INTO `certificate` (`id`, `certificate_no` , `student_id`, `activity_id`, `title`, `issuer`, `issue_time`, `file_url`, `status`, `verify_count`)
VALUES 
(1, 'CERT-2026-ACT2-A89F3B12', 1, 2, '青年AI前沿学术讲座结业证书', '校科技创新协会', NOW(), 'https://images.unsplash.com/photo-1589330694653-dad6bc01cf0f?w=600', 1, 3)
ON DUPLICATE KEY UPDATE `certificate_no` = VALUES(`certificate_no`);

-- -------------------------------------------------------------------
-- 12. AI 客服与平台问答知识库表 (qa_knowledge)
-- 对应需求: 3.15 AI 客服 / 知识内容与运行配置
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `qa_knowledge`;
CREATE TABLE `qa_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识条目ID',
    `category` VARCHAR(50) NOT NULL DEFAULT 'RULE' COMMENT '知识分类: RULE-平台规则, ACTIVITY-活动打卡, GIFT-礼品兑换, SCORE-积分说明, OTHER-其他',
    `question` VARCHAR(255) NOT NULL COMMENT '问题标题/意图匹配关键词',
    `answer` TEXT NOT NULL COMMENT '标准官方解答正文',
    `keywords` VARCHAR(255) DEFAULT NULL COMMENT '检索匹配关键词(逗号分隔)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态: 0-禁用 1-启用',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重(越小越靠前)',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '被咨询查阅次数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_category_status` (`category`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服与平台问答知识库表';

-- 初始AI客服常见问题与知识库种子数据
INSERT INTO `qa_knowledge` (`id`, `category`, `question`, `answer`, `keywords`, `status`, `sort_order`, `view_count`)
VALUES 
(1, 'SCORE', '如何获得积分？积分有什么用？', '在校园活动积分平台中，您可以通过报名参加学校各类社团、学术讲座、文体志愿活动，并在活动现场使用微信扫一扫或手机端扫描动态二维码完成打卡获得对应积分。累积的积分可用于在【积分商城】免费兑换精美文创、电子数码、生活用品及专属权益。', '积分,获得,打卡,兑换', 1, 1, 120),
(2, 'ACTIVITY', '为什么扫码打卡提示“超出打卡范围”或“打卡失败”？', '部分活动开启了GPS定位范围核验。请确保：1. 手机已开启定位权限并允许浏览器获取地理位置；2. 您本人处于活动指定的范围（如体育场内300米半径）内；3. 二维码处于刷新有效期内。', '打卡失败,定位,范围,打卡无效', 1, 2, 85),
(3, 'ACTIVITY', '活动报名人数已满怎么办？', '如果活动开启了候补排队机制，名额满员后您可以点击报名进入“候补队列”。当有正式报名的同学取消或被管理员调整时，系统将按候补排队顺序自动为您递补转正，并通过系统通知提醒您。', '候补,排队,名额已满,报名', 1, 3, 64),
(4, 'GIFT', '礼品兑换后如何领取？可以取消吗？', '兑换成功后，您可以在【个人中心】-【我的兑换】中查看提货核销码（Pickup Code）。请凭提货码前往指定地点（如一站式学生事务中心或联系辅导员）线下核销提货。在管理员确认发放前，如需取消可联系管理员处理，取消后积分将自动退回。', '礼品,领取,提货码,退还积分', 1, 4, 98),
(5, 'RULE', '电子荣誉证书在哪里查看？如何验证真伪？', '对于设置了证书的活动，打卡完成后系统将自动为您颁发电子荣誉证书，您可在【个人中心】-【我的证书】随时查看与下载。每张证书拥有全国唯一的防伪编码与验真二维码，扫码即可核验证书真实性。', '证书,真伪,下载,荣誉', 1, 5, 42)
ON DUPLICATE KEY UPDATE `question` = VALUES(`question`);

SET FOREIGN_KEY_CHECKS = 1;

-- ===================================================================
-- 初始化脚本执行完毕！共包含 12 张生产级业务数据表及完备种子数据。
-- ===================================================================
