-- ===================================================================
-- 学生活动打卡与积分兑换管理系统 - 数据库生产级初始化脚本 (深化优化版)
-- 适用数据库: MySQL 8.0+
-- 字符集: utf8mb4 / 排序规则: utf8mb4_general_ci
-- ===================================================================

CREATE DATABASE IF NOT EXISTS `campus_activity` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `campus_activity`;

-- -------------------------------------------------------------------
-- 1. 系统管理员/后台用户信息表 (sys_user)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
    `password` VARCHAR(100) NOT NULL COMMENT '登录密码(明文/哈希/BCrypt密文)',
    `nickname` VARCHAR(50) NOT NULL COMMENT '真实姓名/显示昵称',
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

-- 初始管理员种子数据
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`, `phone`, `status`)
VALUES 
(1, 'admin', '123', '超级管理员', 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png', 'SUPER_ADMIN', '13800000001', 1),
(2, 'counselor', '123', '李辅导员', 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png', 'COUNSELOR', '13800000002', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- -------------------------------------------------------------------
-- 2. 学生档案信息与核心账户表 (student)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '学生主键ID',
    `student_no` VARCHAR(30) NOT NULL COMMENT '学号(唯一业务标识)',
    `password` VARCHAR(100) NOT NULL DEFAULT '123456' COMMENT '移动端登录密码(初始默认123456)',
    `name` VARCHAR(50) NOT NULL COMMENT '学生真实姓名',
    `gender` TINYINT NOT NULL DEFAULT 1 COMMENT '性别: 1-男 2-女',
    `avatar` VARCHAR(255) DEFAULT 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png' COMMENT '个人头像URL',
    `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序OpenID(用于支持微信一键登录与绑定)',
    `college` VARCHAR(100) DEFAULT NULL COMMENT '所属学院(如计算机学院、艺术设计学院)',
    `major` VARCHAR(100) DEFAULT NULL COMMENT '专业名称(如软件工程、物联网工程)',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '行政班级(如软工2201班)',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系手机号',
    `total_points` INT NOT NULL DEFAULT 0 COMMENT '历史累计获得总积分(用于排行榜与成就等级)',
    `current_points` INT NOT NULL DEFAULT 0 COMMENT '当前可用积分余额(兑换礼品实时扣减)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '学生账户状态: 0-冻结/休学 1-正常在籍',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '档案创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '档案更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`),
    KEY `idx_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生档案与积分主账户表';

-- 初始模拟学生种子数据 (支持排行榜与登录调试)
INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`)
VALUES 
(1, '20220101', '123456', '张伟', 1, '信息工程学院', '计算机科学与技术', '计科2201班', '13911110001', 320, 180, 1),
(2, '20220102', '123456', '王芳', 2, '商学院', '电子商务', '电商2202班', '13911110002', 280, 280, 1),
(3, '20220103', '123456', '陈明', 1, '机械工程学院', '智能制造工程', '智造2201班', '13911110003', 210, 110, 1),
(4, '20220104', '123456', '赵丽', 2, '人文学院', '汉语言文学', '中文2203班', '13911110004', 150, 150, 1),
(5, '20220105', '123456', '孙浩', 1, '信息工程学院', '软件工程', '软工2201班', '13911110005', 90, 40, 1)
ON DUPLICATE KEY UPDATE `student_no` = VALUES(`student_no`);

-- -------------------------------------------------------------------
-- 3. 校园活动信息表 (activity)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动主键ID',
    `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
    `description` TEXT DEFAULT NULL COMMENT '活动富文本/详情说明',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '活动封面图URL',
    `points` INT NOT NULL DEFAULT 10 COMMENT '打卡签到成功可获积分点数',
    `location` VARCHAR(150) DEFAULT NULL COMMENT '活动签到地点名称(如“图书馆一楼报告厅”)',
    `latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '活动定位纬度(防作弊打卡经纬度校验)',
    `longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '活动定位经度(防作弊打卡经纬度校验)',
    `radius` INT NOT NULL DEFAULT 300 COMMENT '允许打卡半径范围(单位:米, 默认300米以内有效)',
    `start_time` DATETIME NOT NULL COMMENT '打卡开启时间',
    `end_time` DATETIME NOT NULL COMMENT '打卡截止时间',
    `max_participants` INT NOT NULL DEFAULT 0 COMMENT '名额上限(0表示不限制人数)',
    `participant_count` INT NOT NULL DEFAULT 0 COMMENT '已成功签到人数',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '活动状态: 0-未开始 1-进行中 2-已截止 3-已下架',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动信息表';

-- 初始活动种子数据
INSERT INTO `activity` (`id`, `title`, `description`, `cover_image`, `points`, `location`, `latitude`, `longitude`, `radius`, `start_time`, `end_time`, `max_participants`, `participant_count`, `status`)
VALUES 
(1, '2026春季校园晨跑早起打卡', '倡导健康作息，在西区操场完成打卡签到，单次奖励15积分。', 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500', 15, '西区田径操场', 30.523412, 114.362145, 500, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 300, 24, 1),
(2, '“青年AI与强国时代”科技前沿讲座', '邀请行业专家进校园分享生成式AI最新技术动态与大学生成长路径。', 'https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=500', 30, '图书馆大报告厅', 30.524100, 114.364200, 200, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 4 HOUR), 150, 68, 1),
(3, '校园美丽工坊：雷锋月志愿清洁服务', '针对教学楼与校园公共绿地开展卫生清洁与环境美化志愿服务。', 'https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500', 25, '学生活动中心门前广场', 30.522800, 114.361500, 300, DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), 80, 0, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- -------------------------------------------------------------------
-- 4. 学生活动打卡/签到记录流水表 (student_activity)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_activity`;
CREATE TABLE `student_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '打卡流水主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID(关联 student.id)',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID(关联 activity.id)',
    `checkin_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打卡签到时间',
    `checkin_latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '学生实际打卡位置纬度',
    `checkin_longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '学生实际打卡位置经度',
    `checkin_image` VARCHAR(255) DEFAULT NULL COMMENT '打卡现场凭证照片URL(自拍或现场活动照)',
    `points_awarded` INT NOT NULL DEFAULT 0 COMMENT '实际获得的积分奖励',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '核验状态: 0-待核验 1-打卡有效(已发分) 2-驳回作废',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '核验反馈/驳回理由',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录写入时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stu_act` (`student_id`, `activity_id`) COMMENT '一学生一活动唯一约束(数据库级杜绝重复打卡刷分)',
    KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生活动签到打卡流水表';

-- 初始打卡示例记录
INSERT INTO `student_activity` (`id`, `student_id`, `activity_id`, `points_awarded`, `status`, `remark`)
VALUES 
(1, 1, 1, 15, 1, 'GPS位置校验通过，打卡有效'),
(2, 2, 1, 15, 1, 'GPS位置校验通过，打卡有效'),
(3, 1, 2, 30, 1, '讲座现场签到成功')
ON DUPLICATE KEY UPDATE `points_awarded` = VALUES(`points_awarded`);

-- -------------------------------------------------------------------
-- 5. 学生积分账户流水台账表 (student_point_record)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_point_record`;
CREATE TABLE `student_point_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID(关联 student.id)',
    `type` TINYINT NOT NULL COMMENT '流水类型: 1-活动打卡奖励(+) 2-商城兑换消耗(-) 3-管理员手动调整(±)',
    `points` INT NOT NULL COMMENT '变动积分值(正数代表增加，负数代表扣减)',
    `balance` INT NOT NULL COMMENT '本次变动后学生可用积分余额(历史对账快照)',
    `description` VARCHAR(255) NOT NULL COMMENT '积分变动明细说明',
    `ref_id` BIGINT DEFAULT NULL COMMENT '业务单据关联合同ID(如 student_activity.id 或 student_gift.id)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水发生时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_student_time` (`student_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生积分变动流水台账表';

-- 初始积分明细种子数据
INSERT INTO `student_point_record` (`id`, `student_id`, `type`, `points`, `balance`, `description`, `ref_id`)
VALUES 
(1, 1, 1, 15, 15, '参与活动打卡奖励【2026春季校园晨跑早起打卡】', 1),
(2, 1, 1, 30, 45, '参与活动打卡奖励【“青年AI与强国时代”科技前沿讲座】', 3),
(3, 1, 2, -50, 180, '兑换积分商城礼品【定制校园帆布袋】', 1)
ON DUPLICATE KEY UPDATE `points` = VALUES(`points`);

-- -------------------------------------------------------------------
-- 6. 积分商城礼品库表 (gift)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `gift`;
CREATE TABLE `gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '礼品主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '礼品名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '礼品简要规格/描述',
    `image` VARCHAR(255) DEFAULT NULL COMMENT '礼品展示图URL',
    `points_required` INT NOT NULL DEFAULT 50 COMMENT '兑换所需积分单价',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '当前可用库存数量',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(高并发抢兑防止超卖)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态: 0-已下架 1-上架展示中',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分礼品商城库表';

-- 初始礼品种子数据
INSERT INTO `gift` (`id`, `name`, `description`, `image`, `points_required`, `stock`, `version`, `status`)
VALUES 
(1, '校园定制环保帆布袋', '加厚纯棉帆布，印有校徽与校训刺绣标志，容量大耐磨。', 'https://images.unsplash.com/photo-1544816155-12df9643f363?w=500', 50, 95, 1, 1),
(2, '高档磨砂双层保温杯', '316L医用不锈钢内胆，24小时长效保温，激光镭雕定制。', 'https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=500', 120, 48, 0, 1),
(3, '运动智能手环 8 代', '全天候心率监测、血氧检测、50米专业防水与长续航。', 'https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?w=500', 300, 15, 0, 1),
(4, '期末备考：图书馆考研VIP自习位月卡', '专属安静自习位，配备独享插座与储物柜。', 'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500', 200, 20, 0, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- -------------------------------------------------------------------
-- 7. 礼品兑换订单与核销发放流转表 (student_gift)
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `student_gift`;
CREATE TABLE `student_gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '兑换订单主键ID',
    `student_id` BIGINT NOT NULL COMMENT '兑换学生ID(关联 student.id)',
    `gift_id` BIGINT NOT NULL COMMENT '兑换礼品ID(关联 gift.id)',
    `points_cost` INT NOT NULL COMMENT '实际扣减积分',
    `pickup_code` VARCHAR(32) NOT NULL COMMENT '核销提货码(8~12位唯一随机串/条形码)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '核销状态: 0-待提货 1-已核销发放 2-已取消/失效退回',
    `pickup_time` DATETIME DEFAULT NULL COMMENT '实际线下领取核销时间',
    `operator_id` BIGINT DEFAULT NULL COMMENT '经办核销管理员ID(关联 sys_user.id，追溯责任人)',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '核销备注/领件人签字说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单兑换时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pickup_code` (`pickup_code`) COMMENT '提货码全局唯一校验',
    KEY `idx_student_id` (`student_id`),
    KEY `idx_gift_id` (`gift_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生礼品兑换申请与核销表';

-- 初始兑换订单模拟数据
INSERT INTO `student_gift` (`id`, `student_id`, `gift_id`, `points_cost`, `pickup_code`, `status`, `pickup_time`, `operator_id`, `remark`)
VALUES 
(1, 1, 1, 50, 'DH20260301001', 1, NOW(), 1, '已于一站式服务大厅窗口领取'),
(2, 2, 2, 120, 'DH20260301002', 0, NULL, NULL, '待领取')
ON DUPLICATE KEY UPDATE `pickup_code` = VALUES(`pickup_code`);
