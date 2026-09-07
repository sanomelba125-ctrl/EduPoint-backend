-- ===================================================================
-- 学生活动打卡与积分兑换管理系统 - 数据库初始化脚本
-- 适用数据库: MySQL 8.0+
-- 字符集: utf8mb4 / 排序规则: utf8mb4_general_ci
-- ===================================================================

CREATE DATABASE IF NOT EXISTS `campus_activity` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `campus_activity`;

-- 1. 系统管理员/用户信息表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
    `password` VARCHAR(100) NOT NULL COMMENT '登录密码(BCrypt密文或哈希)',
    `nickname` VARCHAR(50) NOT NULL COMMENT '用户昵称/真实姓名',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ADMIN' COMMENT '角色: SUPER_ADMIN, ADMIN, COUNSELOR',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户与管理员表';

-- 初始管理员账号: admin / 123
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (1, 'admin', '123', '系统管理员', 'SUPER_ADMIN', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- 2. 学生档案信息表
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_no` VARCHAR(30) NOT NULL COMMENT '学号',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `gender` TINYINT DEFAULT 1 COMMENT '性别: 1-男 2-女',
    `college` VARCHAR(100) DEFAULT NULL COMMENT '所属院系',
    `major` VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '班级',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系手机号',
    `total_points` INT NOT NULL DEFAULT 0 COMMENT '历史累计积分',
    `current_points` INT NOT NULL DEFAULT 0 COMMENT '当前可用积分',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-冻结 1-正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生档案表';

-- 3. 校园活动信息表
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
    `description` TEXT DEFAULT NULL COMMENT '活动详情描述',
    `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '活动封面图',
    `points` INT NOT NULL DEFAULT 10 COMMENT '打卡可获积分',
    `location` VARCHAR(150) DEFAULT NULL COMMENT '打卡地点/场所',
    `start_time` DATETIME NOT NULL COMMENT '打卡开启时间',
    `end_time` DATETIME NOT NULL COMMENT '打卡截止时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-未开始 1-进行中 2-已截止 3-已下架',
    `participant_count` INT NOT NULL DEFAULT 0 COMMENT '已签到人数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园活动表';

-- 4. 学生活动打卡/签到记录表
DROP TABLE IF EXISTS `student_activity`;
CREATE TABLE `student_activity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `checkin_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打卡时间',
    `points_awarded` INT NOT NULL DEFAULT 0 COMMENT '奖励积分点数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态: 0-待审核 1-打卡有效 2-审核不通过',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '核验备注/反馈',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stu_act` (`student_id`, `activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生活动签到打卡流水表';

-- 5. 学生积分账户变动明细表
DROP TABLE IF EXISTS `student_point_record`;
CREATE TABLE `student_point_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `type` TINYINT NOT NULL COMMENT '变动类型: 1-活动打卡获取 2-兑换礼品扣除 3-管理员手动调整',
    `points` INT NOT NULL COMMENT '变动积分(正数增加，负数扣减)',
    `balance` INT NOT NULL COMMENT '变动后账户可用余额',
    `description` VARCHAR(255) NOT NULL COMMENT '变动原因描述',
    `ref_id` BIGINT DEFAULT NULL COMMENT '关联合同/活动ID/礼品兑换单ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生积分流水明细表';

-- 6. 积分礼品库表
DROP TABLE IF EXISTS `gift`;
CREATE TABLE `gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '礼品名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '礼品介绍',
    `image` VARCHAR(255) DEFAULT NULL COMMENT '礼品图片URL',
    `points_required` INT NOT NULL DEFAULT 50 COMMENT '兑换所需积分',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '剩余库存数量',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上架状态: 0-下架 1-上架',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼品库信息表';

-- 7. 礼品兑换订单/核销表
DROP TABLE IF EXISTS `student_gift`;
CREATE TABLE `student_gift` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `gift_id` BIGINT NOT NULL COMMENT '礼品ID',
    `points_cost` INT NOT NULL COMMENT '消耗积分',
    `pickup_code` VARCHAR(32) NOT NULL COMMENT '核销提货码',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待领取 1-已核销发放 2-已取消',
    `pickup_time` DATETIME DEFAULT NULL COMMENT '实际领取核销时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单兑换时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pickup_code` (`pickup_code`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生礼品兑换申请与核销表';
