-- ===================================================================
-- 校园活动积分平台 (EduPoint) - 本地开发与答辩演示全量测试数据包 (demo-data.sql)
-- 
-- 【架构治理说明】：
-- 1. 本文件独立于 Flyway 核心数据库版本迁移流 (db/migration/)；
-- 2. Flyway 严格遵循生产规范，仅管控 DDL 表结构与系统核心元数据演进；
-- 3. 本脚本提供开发、调试、冒烟压测与现场答辩所需的完整业务演示数据；
-- 4. 采用 ON DUPLICATE KEY UPDATE 保证幂等执行，支持多次重复导入。
-- ===================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------------
-- 1. 基础系统管理员与辅导员 (sys_user)
-- 默认密码均为: admin123 (BCrypt 散列)
-- -------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`, `phone`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'admin','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','超级管理员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','SUPER_ADMIN','13800000001',1,'2026-09-08 21:48:56','2026-09-08 21:48:56',0),
(2,'counselor','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','李辅导员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','COUNSELOR','13800000002',1,'2026-09-08 21:48:56','2026-09-08 21:48:56',0)
ON DUPLICATE KEY UPDATE
    `username` = VALUES(`username`),
    `password` = VALUES(`password`),
    `nickname` = VALUES(`nickname`),
    `avatar` = VALUES(`avatar`),
    `role` = VALUES(`role`),
    `phone` = VALUES(`phone`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 2. 演示学生档案信息 (student)
-- 默认初始密码均为: 123456 (BCrypt 散列)
-- -------------------------------------------------------------------
INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `avatar`, `openid`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'20220101','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','张伟',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'计算机科学与技术学院','软件工程','软工2201班','13911110001',335,195,1,'2026-09-08 21:48:57','2026-09-09 16:39:32',0),
(2,'20220102','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','王芳',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'经济管理学院','电子商务','电商2202班','13911110002',280,280,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),
(3,'20220103','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','陈明',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'智能制造工程学院','机械设计制造','智造2201班','13911110003',210,110,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),
(4,'20220104','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','赵丽',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'人文与传媒学院','汉语言文学','中文2203班','13911110004',150,150,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),
(5,'20220105','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','孙浩',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'计算机科学与技术学院','人工智能','智科2201班','13911110005',90,40,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),
(6,'2023010401','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','张晓明',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'信息工程学院','计算机科学与技术','计科2301班','13911110006',1101,1031,1,'2026-09-08 21:54:54','2026-09-14 10:44:50',0),
(7,'2023010101','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','林雨桐',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'外国语学院','商务英语','商英2301班','13911110007',260,260,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),
(8,'2023010202','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','周子涵',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'人工智能学院','智能科学与技术','智科2302班','13911110008',380,310,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),
(9,'2023010303','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','李嘉欣',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'经济管理学院','金融科技','金科2301班','13911110009',190,140,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0)
ON DUPLICATE KEY UPDATE
    `student_no` = VALUES(`student_no`),
    `password` = VALUES(`password`),
    `name` = VALUES(`name`),
    `gender` = VALUES(`gender`),
    `avatar` = VALUES(`avatar`),
    `college` = VALUES(`college`),
    `major` = VALUES(`major`),
    `class_name` = VALUES(`class_name`),
    `phone` = VALUES(`phone`),
    `total_points` = VALUES(`total_points`),
    `current_points` = VALUES(`current_points`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 3. 规范化校园活动信息 (activity)
-- 包含学术讲座、体育晨跑、志愿服务、实训竞赛四大主流分类
-- -------------------------------------------------------------------
INSERT INTO `activity` (`id`, `title`, `category`, `description`, `rule_description`, `cover_image`, `points`, `start_time`, `end_time`, `registration_limit`, `participant_count`, `waitlist_enabled`, `location_name`, `location_address`, `longitude`, `latitude`, `location_radius`, `location_check_enabled`, `certificate_enabled`, `certificate_title`, `certificate_issuer`, `status`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`, `version`)
VALUES
(1,'2026春季校园晨跑健康打卡','SPORTS','坚持早起锻炼，强健体魄！在西区田径场完成晨跑签到即可累计学分。','每天早上 06:30 - 08:30 之间到达田径场，出示动态二维码并提交定位即可打卡。','https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500',15,'2026-03-01 06:30:00','2026-06-30 08:30:00',100,24,1,'西区田径操场','大学园路1号西区体育场',114.362145,30.523412,500,1,0,NULL,NULL,1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0,2),
(2,'“青年AI与新质生产力”前沿学术讲座','ACADEMIC','邀请行业大模型专家进校分享前沿AI工程化架构与新质生产力落地实战。','讲座入场时扫描会场大屏动态二维码完成打卡，全程参与可自动签发电子学术荣誉结业证书。','https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=500',30,'2026-03-10 14:00:00','2026-06-30 17:00:00',80,48,1,'图书馆一楼大报告厅','图书馆主楼A区一层东侧报告厅',114.364200,30.524100,300,1,1,'青年AI前沿学术讲座结业证书','校科技创新协会',1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0,0),
(3,'雷锋月：校园公共绿地清洁志愿服务','VOLUNTEER','践行雷锋精神，共同建设生态美丽校园，开展草坪修整与卫生清洁。','在学生活动中心南门集合签到，统一发放清洁工具，活动出勤后颁发志愿服务证书。','https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500',25,'2026-03-12 09:00:00','2026-06-30 12:00:00',50,1,1,'学生活动中心门前广场','学生活动中心南门广场',114.361500,30.522800,300,0,1,'校园环保志愿服务荣誉证书','校青年志愿者协会',1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0,0),
(4,'创客实训：嵌入式系统设计与开源硬件工作坊','PRACTICE','动手实训，掌握微处理器基础与物联网传感设备快速原型开发。','按时到场并完成实验报告，经导师评定通过即可获实训合格证书。','https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=500',50,'2026-03-15 09:00:00','2026-06-30 18:00:00',40,12,1,'创客实训大楼302','工程创新实验楼302机房',114.363000,30.523000,300,1,1,'嵌入式开发技能认证证书','创新创业教育中心',1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0,0)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `category` = VALUES(`category`),
    `description` = VALUES(`description`),
    `rule_description` = VALUES(`rule_description`),
    `cover_image` = VALUES(`cover_image`),
    `points` = VALUES(`points`),
    `start_time` = VALUES(`start_time`),
    `end_time` = VALUES(`end_time`),
    `registration_limit` = VALUES(`registration_limit`),
    `participant_count` = VALUES(`participant_count`),
    `waitlist_enabled` = VALUES(`waitlist_enabled`),
    `location_name` = VALUES(`location_name`),
    `location_address` = VALUES(`location_address`),
    `longitude` = VALUES(`longitude`),
    `latitude` = VALUES(`latitude`),
    `location_radius` = VALUES(`location_radius`),
    `location_check_enabled` = VALUES(`location_check_enabled`),
    `certificate_enabled` = VALUES(`certificate_enabled`),
    `certificate_title` = VALUES(`certificate_title`),
    `certificate_issuer` = VALUES(`certificate_issuer`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 4. 积分商城礼品数据 (gift)
-- 包含高并发库存预热测试礼品与文创热销品
-- -------------------------------------------------------------------
INSERT INTO `gift` (`id`, `name`, `description`, `image`, `points_required`, `stock`, `total_stock`, `status`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'定制校园环保帆布袋','100%纯棉加厚环保帆布袋，印制学校标志性建筑手绘图案，结实耐用。','https://images.unsplash.com/photo-1544816155-12df9643f363?w=500',50,88,100,1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(2,'智能保温杯 (带数显测温)','316L医用级不锈钢内胆，触控LED液晶显示实时水温，长效锁温24小时。','https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=500',120,45,50,1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(3,'校园文创精装硬壳记事本','道林纸顺滑书写，磁吸搭扣附带金属签名笔，内含学校历史与四季摄影彩页。','https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500',30,150,150,1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(4,'无线降噪蓝牙耳机','双麦克风通话降噪，复合振膜动圈单体，单次续航6小时，配合充电盒24小时。','https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=500',300,15,20,1,1,NULL,'2026-03-01 08:00:00','2026-03-01 08:00:00',0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `image` = VALUES(`image`),
    `points_required` = VALUES(`points_required`),
    `status` = VALUES(`status`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 5. AI 智能客服问答知识库 (qa_knowledge)
-- -------------------------------------------------------------------
INSERT INTO `qa_knowledge` (`id`, `category`, `question`, `answer`, `keywords`, `status`, `sort_order`, `view_count`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'SCORE','如何获得积分？积分有什么用？','在校园活动积分平台中，您可以通过报名参加学校各类社团、学术讲座、文体志愿活动，并在活动现场使用移动端扫描会场动态二维码完成打卡获得对应学分。累积的积分可用于在【积分商城】免费兑换精美文创、电子数码、生活用品及专属权益。','积分,获得,打卡,兑换,学分',1,1,120,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(2,'ACTIVITY','为什么扫码打卡提示“超出打卡范围”或“打卡失败”？','部分活动开启了GPS硬件定位范围核验。请确保：1. 手机已开启定位权限并允许浏览器获取地理位置；2. 您本人处于活动指定的范围（如体育场内300米半径）内；3. 二维码处于刷新时间窗内。','打卡失败,定位,范围,打卡无效,GPS',1,2,85,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(3,'ACTIVITY','活动报名人数已满怎么办？','如果活动开启了候补排队机制，名额满员后您可以点击报名进入“候补队列”。当有正式报名的同学取消报名时，系统将按候补排队序号自动为您顺延递补转正，并即时通过站内通知提醒您。','候补,排队,名额已满,报名,转正',1,3,64,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(4,'GIFT','礼品兑换后如何领取？可以取消吗？','兑换成功后，您可以在【个人中心】-【我的兑换】中查看专属核销提货二维码（Pickup Code）。请凭提货二维码前往指定服务网点出示核销。在管理员核销出库前，若需取消可申请退单，审核通过后积分将原子全额返还。','礼品,领取,提货码,退还积分,核销',1,4,98,'2026-03-01 08:00:00','2026-03-01 08:00:00',0),
(5,'CERTIFICATE','电子荣誉证书在哪里查看？如何验证真伪？','对于设置了电子证书的活动，打卡完成后系统将自动为您颁发荣誉电子证书，您可在【个人中心】-【我的证书】随时查看与高清导出。每张证书拥有全国唯一的防伪证书编号与加密验真二维码，公开扫码即可核验证书真伪。','证书,真伪,下载,荣誉,防伪',1,5,42,'2026-03-01 08:00:00','2026-03-01 08:00:00',0)
ON DUPLICATE KEY UPDATE
    `category` = VALUES(`category`),
    `question` = VALUES(`question`),
    `answer` = VALUES(`answer`),
    `keywords` = VALUES(`keywords`),
    `status` = VALUES(`status`),
    `sort_order` = VALUES(`sort_order`),
    `deleted` = VALUES(`deleted`);

SET FOREIGN_KEY_CHECKS = 1;
