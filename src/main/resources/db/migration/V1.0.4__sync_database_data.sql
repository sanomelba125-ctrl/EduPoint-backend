-- ===================================================================
-- V1.0.4: 同步最新数据库业务与测试演示数据 (幂等导入)
-- 包含用户、学生、活动、礼品、打卡记录、积分明细、证书、知识库等全量最新数据
-- ===================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------------
-- 1. 系统管理员与后台用户数据 (sys_user)
-- -------------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`, `phone`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'admin','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','超级管理员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','SUPER_ADMIN','13800000001',1,'2026-09-08 21:48:56','2026-09-08 21:48:56',0),(2,'counselor','$2a$10$Gv7lCOIQdn6JqzJ2JQ/2EOMVyp0prFXaWu9jNzXNttHsTyVfbbjiO','李辅导员','https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png','COUNSELOR','13800000002',1,'2026-09-08 21:48:56','2026-09-08 21:48:56',0)
ON DUPLICATE KEY UPDATE
    `username` = VALUES(`username`),
    `password` = VALUES(`password`),
    `nickname` = VALUES(`nickname`),
    `avatar` = VALUES(`avatar`),
    `role` = VALUES(`role`),
    `phone` = VALUES(`phone`),
    `status` = VALUES(`status`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 2. 学生档案信息与核心账户数据 (student)
-- -------------------------------------------------------------------
INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `avatar`, `openid`, `college`, `major`, `class_name`, `phone`, `total_points`, `current_points`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'20220101','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','张伟',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'信息工程学院','计算机科学与技术','计科2201班','13911110001',335,195,1,'2026-09-08 21:48:57','2026-09-09 16:39:32',0),(2,'20220102','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','王芳',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'商学院','电子商务','电商2202班','13911110002',280,280,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(3,'20220103','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','陈明',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'机械工程学院','智能制造工程','智造2201班','13911110003',210,110,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(4,'20220104','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','赵丽',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'人文学院','汉语言文学','中文2203班','13911110004',150,150,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(5,'20220105','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','孙浩',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'信息工程学院','软件工程','软工2201班','13911110005',90,40,1,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(6,'2023010401','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','张晓明',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'信息工程学院','软件工程','软工2301班','13911110006',1101,1031,1,'2026-09-08 21:54:54','2026-09-14 10:44:50',0),(7,'2023010101','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','林雨桐',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'外国语学院','商务英语','商英2301班','13911110007',260,260,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),(8,'2023010202','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','周子涵',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'人工智能学院','人工智能','智科2302班','13911110008',380,310,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),(9,'2023010303','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','李嘉欣',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'经济管理学院','金融科技','金科2301班','13911110009',190,140,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),(10,'2024010105','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','刘思源',1,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'信息工程学院','网络空间安全','网安2401班','13911110010',150,150,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0),(11,'2024010206','$2a$10$H5IhVx.AarNhFcLtYlg6hu49GQ8.0btHXfRJ9dwh37uF9UKJq6Qru','黄小雅',2,'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png',NULL,'艺术设计学院','视觉传达设计','视传2402班','13911110011',80,80,1,'2026-09-11 08:56:14','2026-09-11 08:56:26',0)
ON DUPLICATE KEY UPDATE
    `student_no` = VALUES(`student_no`),
    `password` = VALUES(`password`),
    `name` = VALUES(`name`),
    `gender` = VALUES(`gender`),
    `avatar` = VALUES(`avatar`),
    `openid` = VALUES(`openid`),
    `college` = VALUES(`college`),
    `major` = VALUES(`major`),
    `class_name` = VALUES(`class_name`),
    `phone` = VALUES(`phone`),
    `total_points` = VALUES(`total_points`),
    `current_points` = VALUES(`current_points`),
    `status` = VALUES(`status`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 3. 校园活动信息数据 (activity)
-- -------------------------------------------------------------------
INSERT INTO `activity` (`id`, `title`, `category`, `description`, `rule_description`, `cover_image`, `points`, `start_time`, `end_time`, `registration_limit`, `participant_count`, `waitlist_enabled`, `location_name`, `location_address`, `longitude`, `latitude`, `location_radius`, `location_check_enabled`, `certificate_enabled`, `certificate_title`, `certificate_issuer`, `status`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`, `version`)
VALUES
(1,'2026春季校园晨跑健康打卡','SPORTS','坚持早起锻炼，强健体魄！在西区田径场完成晨跑签到即可累计积分。','每天早上 06:30 - 08:30 之间到达田径场，出示动态二维码并提交定位即可打卡。','https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500',15,'2026-09-07 21:48:57','2026-09-22 21:48:57',100,24,1,'西区田径操场','湖北省武汉市洪山区大学园路1号西区体育场',114.362145,30.523412,500,1,0,NULL,NULL,1,1,NULL,'2026-09-08 21:48:57','2026-09-09 10:43:45',0,2),(2,'“青年AI与新质生产力”前沿学术讲座','ACADEMIC','邀请行业知名技术专家进校分享大模型与前沿技术实战，拓宽学术视野。','讲座入场时扫描会场动态二维码完成打卡，全程参与可获得电子学术结业证书。','https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=500',30,'2026-09-08 19:48:57','2026-09-09 03:48:57',50,48,1,'图书馆一楼大报告厅','图书馆主楼A区一层东侧报告厅',114.364200,30.524100,200,1,1,'AI前沿学术讲座参与证书','校科技创新与创业协会',1,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0,0),(3,'雷锋月：校园公共绿地清洁志愿服务','VOLUNTEER','践行雷锋精神，共同建设美丽校园，开展草坪修整与卫生清洁。','在学生活动中心集合签到，统一发放清洁工具，活动结束后颁发志愿服务证书。','https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500',25,'2026-09-10 21:48:57','2026-09-13 21:48:57',30,1,1,'学生活动中心门前广场','学生活动中心南门广场旗杆下',114.361500,30.522800,300,0,1,'校园环保志愿服务荣誉证书','校青年志愿者协会',0,1,NULL,'2026-09-08 21:48:57','2026-09-09 15:46:50',0,23),(4,'读书月：经典品读与思辨沙龙 (往期归档)','PRACTICE','阅读经典名著，分享读书感悟与体会。','现场签到并参与讨论。','https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500',10,'2026-08-29 21:48:57','2026-09-06 21:48:57',40,40,0,'博学楼201教室','博学楼二楼西侧多媒体教室',114.363000,30.523000,300,0,0,NULL,NULL,2,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0,0),(101,'【测试用例01】火热报名中-名额充足','ACADEMIC','测试用例01：活动处于进行中且名额充裕。测试学生点击卡片进入详情页后，名额卡片显示充足比例，底部主按钮显示“立即报名 (+30 pts)”，点击可顺利完成正式报名并占位。','到达活动现场即可参与，正选名额充裕，出勤后可获得 30 积分。','https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=500',30,'2026-09-08 08:00:00','2026-09-28 22:00:00',50,13,1,'信息学部报告厅A101','武汉市洪山区珞喻路129号',114.364200,30.524100,300,0,0,NULL,NULL,1,1,1,'2026-09-09 14:41:11','2026-09-09 16:22:48',0,9),(102,'【测试用例02】名额已满额-开启候补排队','VOLUNTEER','测试用例02：正选名额已达上限（5/5），且后台开启候补。测试详情页显示“可候补”徽章，底部按钮显示“名额已满 · 申请候补排队 (预排第1位)”，点击后可成功进入候补排队队列。','正选已满，点击申请候补队列，若有同学退报将按顺序自动顺延递补。','https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500',40,'2026-09-08 08:00:00','2026-09-28 22:00:00',5,5,1,'青年志愿者服务站','学生活动中心南门',114.361500,30.522800,300,0,0,NULL,NULL,1,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,0),(103,'【测试用例03】名额已满额-未开启候补','SPORTS','测试用例03：名额已满额（20/20），未开放候补。测试列表卡片显示“已满额”，详情页底部按钮显示“名额已满额 (未开放候补)”并置灰禁用，无法交互。','名额已满，未开放排队候补功能。','https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=500',20,'2026-09-08 08:00:00','2026-09-28 22:00:00',20,20,0,'西区体育场','体育馆一层羽毛球馆',114.362145,30.523412,300,0,0,NULL,NULL,1,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,0),(104,'【测试用例04】未开始活动-支持提前报名','PRACTICE','测试用例04：活动尚未开展（开始时间在未来）。测试列表卡片显示“未开始”徽章，详情页底部按钮显示“提前报名 (+50 pts)”。报名后按钮更新为“等待开展 · 届时现场打卡”。','活动将于10月中旬开展，支持提前预约正选名额。','https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=500',50,'2026-10-15 09:00:00','2026-10-20 18:00:00',60,8,1,'创客实训大楼302','福建省厦门市集美区后溪镇理工路610号厦门理工学院(集美校区)',118.085046,24.624114,300,0,0,'','',0,1,1,'2026-09-09 14:41:11','2026-09-09 16:23:19',0,5),(105,'【测试用例05】已截止活动-活动已结束','OTHER','测试用例05：活动已过截止时间。测试列表卡片显示“已截止”徽章，详情页底部按钮显示“活动已截止 · 报名已结束”且置灰禁用，无法报名或打卡。','活动起止时间已截止，停止所有出勤打卡。','https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500',15,'2026-08-20 09:00:00','2026-09-01 18:00:00',40,40,0,'博学楼201教室','博学楼西侧',114.363000,30.523000,300,0,0,NULL,NULL,2,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,0),(106,'【测试用例06】现场扫码打卡-开启GPS核验与颁发证书','ACADEMIC','测试用例06：全特性活动。开启GPS地理围栏（半径500m），开启结业电子证书认证。测试详情页展示“颁发结业证书”和GPS雷达围栏模拟圈，测试扫码打卡及证书发放。','现场大屏每10秒刷新动态考勤二维码，到达会场500m定位范围内扫码即可完成出勤，出勤后自动颁发荣誉电子证书。','https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=500',80,'2026-09-08 08:00:00','2026-09-30 22:00:00',80,27,1,'信息学部401学术大厅','武汉市洪山区珞喻路129号信息学部',114.364200,30.524100,500,1,1,'网络安全攻防实训结业证书','校网络空间安全学院 / 科技协会',1,1,1,'2026-09-09 14:41:11','2026-09-11 10:42:54',0,6),(107,'【测试用例07】已下架归档-大厅不展示','OTHER','测试用例07：已下架归档活动。测试活动大厅默认过滤隐藏不显示；若在浏览器直接访问 id=107 详情页，显示“已下架”徽章，底部按钮显示“活动已下架归档”。','该活动已归档入库。','',10,'2026-06-01 09:00:00','2027-06-15 18:00:00',50,10,0,'校史馆三楼','校史馆东门',114.360000,30.520000,300,0,0,'','',3,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,1),(108,'【测试用例08】当前用户已正式报名-待打卡与支持退报','VOLUNTEER','测试用例08：当前登录学生（张伟，studentId=1）已预先正式报名该活动。测试详情页显示“您已正式报名成功！”绿色横幅，底部显示“取消报名”与“现场扫码打卡”按钮，支持退报测试。','已正式报名，可在活动进行期间前往现场完成出勤，或点击取消报名释放名额。','https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=500',45,'2026-09-08 08:00:00','2026-09-28 22:00:00',50,16,1,'青年志愿者之家','学生活动中心南门',114.363000,30.522000,300,0,0,NULL,NULL,1,1,1,'2026-09-09 14:41:11','2026-09-09 16:33:32',0,10),(109,'【测试用例09】当前用户处于候补中-支持退出候补','PRACTICE','测试用例09：正选名额已满（10/10），当前登录学生（张伟，studentId=1）已排在候补第1位。测试详情页显示“正在候补排队中（当前排第1位）”横幅，底部按钮显示“候补排队中 (第 1 位 · 等待递补)”置灰，左侧显示“退出候补”按钮，支持退出候补测试。','正选名额已满，学生排队等待递补。点击退出候补可主动放弃排队名额。','https://images.unsplash.com/photo-1508614589041-895b88991e3e?w=500',35,'2026-09-08 08:00:00','2026-09-28 22:00:00',10,10,1,'无人机航模科技馆','工科大楼北翼',114.366000,30.525000,300,0,0,NULL,NULL,1,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,0),(110,'【测试用例10】当前用户已完成出勤-积分已入账','ACADEMIC','测试用例10：当前登录学生（张伟，studentId=1）已完成出勤打卡。测试详情页底部按钮显示“已完成出勤打卡 · 学分已入账”；“我的活动记录”中“已完成”标签页可查看此出勤记录与+50 pts入账明细。','已核验通过，不可重复出勤或退报。','https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=500',50,'2026-09-08 08:00:00','2026-09-28 22:00:00',100,50,1,'计算机学院报告厅B','计科楼一层',114.364000,30.524000,300,0,1,'ACM程序设计秋季热身赛荣誉证书','校程序设计竞赛委员会',1,1,1,'2026-09-09 14:41:11','2026-09-09 14:41:11',0,0),(114514,'检查并发用例','OTHER','test','无',NULL,100,'2026-09-11 09:00:50','2027-09-11 09:00:53',100,100,1,'local1','local2',114.364000,30.524000,300,1,0,'证书名字','单位',1,1,1,'2026-09-11 09:05:42','2026-09-14 10:26:47',0,45),(114515,'检查并发用例','OTHER','test','无','',100,'2026-09-11 09:00:50','2027-09-11 09:00:53',100,71,1,'local1','福建省厦门市集美区后溪镇理工路618号厦门理工学院(集美校区)',118.084569,24.623335,1000,1,0,'证书名字','单位',1,1,1,'2026-09-11 09:05:42','2026-09-14 08:52:26',0,34),(114516,'面向ai编程','ACADEMIC','超级加分项先到先得','只有一个人能签到成功','',500,'2026-09-11 00:00:00','2026-09-13 00:00:00',1,1,0,'厦门理工学院(集美校区)','理工路600号',118.087898,24.623911,1000,1,1,'面向ai编程证书','ai发展协会',1,1,1,'2026-09-11 10:59:36','2026-09-11 11:00:07',0,2)
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
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`),
    `version` = VALUES(`version`);

-- -------------------------------------------------------------------
-- 4. 积分商城礼品数据 (gift)
-- -------------------------------------------------------------------
INSERT INTO `gift` (`id`, `name`, `description`, `image`, `points_required`, `total_stock`, `stock`, `version`, `status`, `created_by`, `updated_by`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'校园定制环保帆布袋','加厚纯棉帆布，印有校徽与校训刺绣标志，容量大结实耐用。','https://images.unsplash.com/photo-1544816155-12df9643f363?w=500',50,100,95,1,1,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(2,'高档磨砂双层保温杯','316L食品级医用不锈钢内胆，长效保温保冷，激光镭雕定制。','https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=500',120,50,48,0,1,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(3,'运动智能手环 8 代','全天候健康心率监测、血氧检测、50米专业防水与超长续航。','https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?w=500',300,20,15,0,1,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(4,'期末备考：图书馆考研VIP自习位月卡','专属安静自习位，配备独享充电插座、护眼台灯与储物柜。','https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=500',200,30,20,0,1,1,NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `description` = VALUES(`description`),
    `image` = VALUES(`image`),
    `points_required` = VALUES(`points_required`),
    `total_stock` = VALUES(`total_stock`),
    `stock` = VALUES(`stock`),
    `version` = VALUES(`version`),
    `status` = VALUES(`status`),
    `created_by` = VALUES(`created_by`),
    `updated_by` = VALUES(`updated_by`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 5. 活动动态打卡二维码数据 (activity_checkin_code)
-- -------------------------------------------------------------------
INSERT INTO `activity_checkin_code` (`id`, `activity_id`, `nonce`, `sign`, `expire_time`, `status`, `used_by_student_id`, `used_time`, `create_time`, `update_time`)
VALUES
(1,1,'NONCE_20260301_晨跑_001','SIGN_晨跑_ABC1234567890','2026-09-08 21:48:57',1,1,'2026-09-08 21:48:57','2026-09-08 21:48:57','2026-09-08 21:48:57'),(2,1,'NONCE_20260301_晨跑_002','SIGN_晨跑_DEF1234567890','2026-09-08 21:48:57',1,2,'2026-09-08 21:48:57','2026-09-08 21:48:57','2026-09-08 21:48:57'),(3,2,'NONCE_20260301_讲座_001','SIGN_讲座_GHI1234567890','2026-09-08 21:48:57',1,1,'2026-09-08 21:48:57','2026-09-08 21:48:57','2026-09-08 21:48:57'),(4,2,'NONCE_CURRENT_ACTIVE_TOKEN','SIGN_讲座_ACTIVE_SECRET','2026-09-08 22:18:57',2,NULL,NULL,'2026-09-08 21:48:57','2026-09-08 22:17:59'),(5,5,'NONCE_4884420D068A42','SIGN_5LMWWVZV9KY2WVPCKU','2026-09-08 22:06:05',2,NULL,NULL,'2026-09-08 22:01:05','2026-09-08 22:01:05'),(6,5,'NONCE_ECEEB659954341','SIGN_XJ5QX9_BKTF5LGWNKU','2026-09-08 22:06:05',0,NULL,NULL,'2026-09-08 22:01:05','2026-09-08 22:01:05'),(7,3,'NONCE_9BDBD4BFC7474E','SIGN_ACXVUAYHELMMB3WDU8','2026-09-08 22:09:08',2,NULL,NULL,'2026-09-08 22:04:08','2026-09-08 22:16:26'),(8,3,'NONCE_D10F76440C5840','SIGN_T7YUMXOUTO1MMS-WEG','2026-09-08 22:21:27',2,NULL,NULL,'2026-09-08 22:16:26','2026-09-08 22:17:56'),(9,3,'NONCE_1D1381013CE748','SIGN_KLFTYXR7QE8VCRFR7R','2026-09-08 22:22:57',0,NULL,NULL,'2026-09-08 22:17:56','2026-09-08 22:17:56'),(10,2,'NONCE_2FA3B693344E45','SIGN_U52R1YDIAMWRQPGQMJ','2026-09-08 22:23:00',2,NULL,NULL,'2026-09-08 22:17:59','2026-09-09 17:44:54'),(11,1,'NONCE_7651819A120A4A','SIGN_3CTDE38HTWMX20RXNH','2026-09-08 22:33:08',2,NULL,NULL,'2026-09-08 22:28:07','2026-09-09 16:06:02'),(12,6,'NONCE_714599ABC88B46','SIGN_8YRQAAGLNFFMR6GA5T','2026-09-08 22:46:14',0,NULL,NULL,'2026-09-08 22:41:13','2026-09-08 22:41:13'),(13,9,'NONCE_FB3E7DEADC9443','SIGN_WKITSGUBFRBWAOU6IE','2026-09-09 09:33:13',2,NULL,NULL,'2026-09-09 09:34:12','2026-09-09 09:34:13'),(14,9,'NONCE_EF7ACCF8B8F148','SIGN_MWE5WWOXIWCI2G1PHV','2026-09-09 09:39:13',1,6,'2026-09-09 09:34:13','2026-09-09 09:34:13','2026-09-09 09:34:13'),(15,104,'NONCE_B495D6DDF7DA49','SIGN_KKSJGQRGDAE6EXWU5Q','2026-09-09 16:06:10',2,NULL,NULL,'2026-09-09 16:01:09','2026-09-09 16:01:32'),(16,104,'NONCE_6204CE7B073A44','SIGN__TUABNEGK2QDK-A6NH','2026-09-09 16:06:33',2,NULL,NULL,'2026-09-09 16:01:32','2026-09-09 16:01:45'),(17,104,'NONCE_F6ECCDE9AF3646','SIGN_1XMFS1JYPAY4TFZAG6','2026-09-09 16:06:45',2,NULL,NULL,'2026-09-09 16:01:45','2026-09-09 16:01:49'),(18,104,'NONCE_3C238870959F46','SIGN_V1CAA8OUVM4WQRM-QY','2026-09-09 16:06:49',2,NULL,NULL,'2026-09-09 16:01:49','2026-09-09 16:01:51'),(19,104,'NONCE_C6DA52EBC48C4E','SIGN_7TE4P6PY7QCWWEMT14','2026-09-09 16:06:51',2,NULL,NULL,'2026-09-09 16:01:51','2026-09-09 16:25:56'),(20,1,'NONCE_390CC5101A0F4D','SIGN_DFX6PEANQB3SHHD1-U','2026-09-09 16:11:02',2,NULL,NULL,'2026-09-09 16:06:02','2026-09-09 16:06:42'),(21,1,'NONCE_8F47606F92F849','SIGN_K_XYAXVEKWBLL_9K2L','2026-09-09 16:11:43',2,NULL,NULL,'2026-09-09 16:06:42','2026-09-09 16:07:13'),(22,1,'NONCE_5520F422E18547','SIGN_S7UBVYT3HGX9SL2T4U','2026-09-09 16:12:14',2,NULL,NULL,'2026-09-09 16:07:13','2026-09-09 16:38:00'),(23,104,'NONCE_39A3F84813E748','SIGN_JNMX9KIGYJSH-SYDFO','2026-09-09 16:30:56',2,NULL,NULL,'2026-09-09 16:25:56','2026-09-09 16:34:32'),(24,104,'NONCE_00967C0C8AC341','SIGN_JMG7JNC79GAOOUTEH9','2026-09-09 16:39:32',2,NULL,NULL,'2026-09-09 16:34:32','2026-09-09 16:34:45'),(25,104,'NONCE_BDA8EC6578CF47','SIGN_M7CSP22S1UDYHUBYB1','2026-09-09 16:39:45',2,NULL,NULL,'2026-09-09 16:34:45','2026-09-09 16:34:55'),(26,104,'NONCE_76C274C3193947','SIGN_FTMEDIYWKPR1MOHW3V','2026-09-09 16:39:55',2,NULL,NULL,'2026-09-09 16:34:55','2026-09-09 16:35:08'),(27,104,'NONCE_4B09BC5D70004B','SIGN_MG80UONOWRAJZQ_RSR','2026-09-09 16:40:08',2,NULL,NULL,'2026-09-09 16:35:08','2026-09-09 17:07:26'),(28,1,'NONCE_B9186967CCB248','SIGN_XGFNNNBAYWYGB0_VIF','2026-09-09 16:43:00',2,NULL,NULL,'2026-09-09 16:38:00','2026-09-09 16:38:02'),(29,1,'NONCE_4FEADD3EE0BC46','SIGN_EIPJSQEXT6NVJQBFTT','2026-09-09 16:43:03',1,1,'2026-09-09 16:39:32','2026-09-09 16:38:02','2026-09-09 16:39:32'),(30,104,'NONCE_7619EB675B0340','SIGN_OTGRQU8X4C6FJSY_ET','2026-09-09 17:12:27',2,NULL,NULL,'2026-09-09 17:07:26','2026-09-09 17:44:03'),(31,101,'NONCE_DEMO_TEST_101','SIGN_DEMO_TEST_101','2026-09-09 23:59:59',2,NULL,NULL,'2026-09-09 17:25:14','2026-09-11 10:17:15'),(32,104,'NONCE_CEEB575711C343','SIGN_EV1IFGSZCP4BP_DFUO','2026-09-09 17:49:03',2,NULL,NULL,'2026-09-09 17:44:03','2026-09-09 17:44:11'),(33,104,'NONCE_03734D6B59134D','SIGN_4XKV5KAPMN58KZOFSB','2026-09-09 17:49:11',2,NULL,NULL,'2026-09-09 17:44:11','2026-09-11 10:43:22'),(34,2,'NONCE_3C8E54FDA15542','SIGN_171SXXEOJX2KKYF_JB','2026-09-09 17:49:55',2,NULL,NULL,'2026-09-09 17:44:54','2026-09-09 17:44:57'),(35,2,'NONCE_F7D6FC4AA2C34C','SIGN_L0UFRNGMMHK6QPZAGF','2026-09-09 17:49:57',0,NULL,NULL,'2026-09-09 17:44:57','2026-09-09 17:44:57'),(36,106,'NONCE_2F9C3F3B84F947','SIGN_LK_PFFUGDTFB_UCNBF','2026-09-09 17:51:19',2,NULL,NULL,'2026-09-09 17:46:18','2026-09-09 17:46:21'),(37,106,'NONCE_1298C23B3A4D45','SIGN_QBUNRHKTWTZIL23OFC','2026-09-09 17:51:21',2,NULL,NULL,'2026-09-09 17:46:21','2026-09-09 17:47:44'),(38,106,'NONCE_1F64C78A66DC42','SIGN_DZBRMTQPOZHJYGO75R','2026-09-09 17:52:45',2,NULL,NULL,'2026-09-09 17:47:45','2026-09-11 10:43:28'),(39,101,'NONCE_252A164EE83C49','SIGN_00NV6VRCDP85G2V9XF','2026-09-11 10:22:16',0,NULL,NULL,'2026-09-11 10:17:16','2026-09-11 10:17:16'),(40,104,'NONCE_A16344FEB54E49','SIGN_KRFNJEVGWQFER6XMTZ','2026-09-11 10:48:22',2,NULL,NULL,'2026-09-11 10:43:22','2026-09-11 10:49:32'),(41,106,'NONCE_9058B85576E34B','SIGN_JRF1KE7WRNNZ62LSYL','2026-09-11 10:48:28',2,NULL,NULL,'2026-09-11 10:43:28','2026-09-11 10:43:33'),(42,106,'NONCE_B705B8415C6B4F','SIGN_DOIIQEAMLJ6MNOGFY4','2026-09-11 10:48:33',2,NULL,NULL,'2026-09-11 10:43:33','2026-09-11 10:43:34'),(43,106,'NONCE_8028155D23C94A','SIGN_6AT-ESIV8ZHXH0DGI1','2026-09-11 10:48:34',2,NULL,NULL,'2026-09-11 10:43:34','2026-09-11 10:50:33'),(44,104,'NONCE_13A72CDCCE2640','SIGN_CASDEVT3LBL5TRDIWW','2026-09-11 10:54:33',2,NULL,NULL,'2026-09-11 10:49:32','2026-09-11 10:49:36'),(45,104,'NONCE_A207636AE28D46','SIGN_MYER67UGGWXUOEM79M','2026-09-11 10:54:37',2,NULL,NULL,'2026-09-11 10:49:36','2026-09-11 11:06:50'),(46,106,'NONCE_5F1EF78595F042','SIGN_AVVIPXWCVV8ZNY5BB1','2026-09-11 10:55:34',2,NULL,NULL,'2026-09-11 10:50:33','2026-09-11 10:50:37'),(47,106,'NONCE_8B62C06740F640','SIGN_2HQ8YA4SLFQHW2POFR','2026-09-11 10:55:37',1,6,'2026-09-11 10:51:02','2026-09-11 10:50:37','2026-09-11 10:51:02'),(48,110,'NONCE_8D68C7E0773E47','SIGN_V2VFV-IAXL6GK4NRKZ','2026-09-11 11:00:55',0,NULL,NULL,'2026-09-11 10:55:55','2026-09-11 10:55:55'),(49,114516,'NONCE_9B86058A832B46','SIGN_UEXBOBDC2WZ5I31XA5','2026-09-11 11:04:52',2,NULL,NULL,'2026-09-11 10:59:51','2026-09-11 11:00:16'),(50,114516,'NONCE_9E6793C208B947','SIGN_QO7NTPDVX0H1VPDKNB','2026-09-11 11:05:17',1,6,'2026-09-11 11:00:38','2026-09-11 11:00:16','2026-09-11 11:00:38'),(51,104,'NONCE_ED38C56ED12E4D','SIGN_-XP_URMQAUT2KSHGYG','2026-09-11 11:11:50',2,NULL,NULL,'2026-09-11 11:06:50','2026-09-11 11:09:26'),(52,104,'NONCE_F12DD212210E43','SIGN_2FYNSOJNQ7AGRQDDKI','2026-09-11 11:14:26',2,NULL,NULL,'2026-09-11 11:09:26','2026-09-14 10:32:14'),(53,114515,'NONCE_B8D472C084E341','SIGN_V_VE7DYHMKRNCHZWWA','2026-09-14 10:35:13',2,NULL,NULL,'2026-09-14 10:30:13','2026-09-14 10:34:31'),(54,104,'NONCE_F412F36DC7674B','SIGN_-R7NPCBFLDTH1GRJ8X','2026-09-14 10:37:15',0,NULL,NULL,'2026-09-14 10:32:14','2026-09-14 10:32:14'),(55,114515,'NONCE_63D67604204046','SIGN_BFLA_QVZTTE4YQPBBC','2026-09-14 10:39:32',2,NULL,NULL,'2026-09-14 10:34:31','2026-09-14 10:39:31'),(56,114515,'NONCE_1752EBA5CFEF4F','SIGN_USUIOAQ2L7MYICCSOC','2026-09-14 10:44:32',2,NULL,NULL,'2026-09-14 10:39:31','2026-09-14 10:44:22'),(57,114515,'NONCE_C7C3A3F9D8BA4C','SIGN_Z6PHJSVXSW8UTP0ANL','2026-09-14 10:49:22',1,6,'2026-09-14 10:44:50','2026-09-14 10:44:22','2026-09-14 10:44:50')
ON DUPLICATE KEY UPDATE
    `activity_id` = VALUES(`activity_id`),
    `nonce` = VALUES(`nonce`),
    `sign` = VALUES(`sign`),
    `expire_time` = VALUES(`expire_time`),
    `status` = VALUES(`status`),
    `used_by_student_id` = VALUES(`used_by_student_id`),
    `used_time` = VALUES(`used_time`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`);

-- -------------------------------------------------------------------
-- 6. 活动候补排队数据 (activity_waitlist)
-- -------------------------------------------------------------------
INSERT INTO `activity_waitlist` (`id`, `activity_id`, `student_id`, `queue_number`, `status`, `wait_time`, `promoted_time`, `create_time`, `update_time`, `deleted`)
VALUES
(1,2,5,1,1,'2026-09-08 20:48:57',NULL,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(2,7,3,1,2,'2026-09-09 09:32:35','2026-09-09 09:32:35','2026-09-09 09:32:34','2026-09-09 09:32:34',0),(3,8,4,1,1,'2026-09-09 09:33:33',NULL,'2026-09-09 09:33:33','2026-09-09 09:33:33',0),(4,8,5,2,1,'2026-09-09 09:33:34',NULL,'2026-09-09 09:33:33','2026-09-09 09:33:33',0),(5,8,6,3,1,'2026-09-09 09:33:34',NULL,'2026-09-09 09:33:33','2026-09-09 09:33:33',0),(6,8,1,4,3,'2026-09-09 11:32:23',NULL,'2026-09-09 11:32:23','2026-09-09 11:32:23',0),(7,7,1,4,3,'2026-09-09 13:35:20',NULL,'2026-09-09 11:50:03','2026-09-09 11:50:03',0),(9,109,1,7,3,'2026-09-09 15:58:06',NULL,'2026-09-09 14:41:11','2026-09-09 14:41:11',0),(10,102,1,1,1,'2026-09-11 08:53:19',NULL,'2026-09-09 14:44:32','2026-09-09 14:44:32',0),(11,114514,6,1,2,'2026-09-11 09:46:59','2026-09-11 09:47:14','2026-09-11 09:09:00','2026-09-11 09:09:00',0),(12,114514,5,2,2,'2026-09-11 09:47:02','2026-09-11 09:47:25','2026-09-11 09:13:52','2026-09-11 09:13:52',0),(13,114514,1,1,2,'2026-09-11 09:28:37','2026-09-11 09:28:43','2026-09-11 09:16:05','2026-09-11 09:16:05',0)
ON DUPLICATE KEY UPDATE
    `activity_id` = VALUES(`activity_id`),
    `student_id` = VALUES(`student_id`),
    `queue_number` = VALUES(`queue_number`),
    `status` = VALUES(`status`),
    `wait_time` = VALUES(`wait_time`),
    `promoted_time` = VALUES(`promoted_time`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 7. 学生活动参与/报名/打卡记录数据 (student_activity)
-- -------------------------------------------------------------------
INSERT INTO `student_activity` (`id`, `student_id`, `activity_id`, `signup_time`, `checkin_time`, `checkin_latitude`, `checkin_longitude`, `checkin_distance`, `qr_nonce_used`, `points_awarded`, `status`, `remark`, `create_time`, `update_time`, `deleted`)
VALUES
(1,1,1,'2026-09-07 21:48:57','2026-09-09 16:39:32',30.523418,114.362140,1,'NONCE_4FEADD3EE0BC46',15,2,'GPS范围校验通过，打卡有效','2026-09-08 21:48:57','2026-09-09 16:18:01',0),(2,2,1,'2026-09-07 21:48:57','2026-09-08 21:48:57',30.523410,114.362140,6,'NONCE_20260301_晨跑_002',15,2,'GPS范围校验通过，打卡有效','2026-09-08 21:48:57','2026-09-08 21:48:57',0),(3,1,2,'2026-09-08 19:48:57','2026-09-08 21:48:57',30.524105,114.364210,12,'NONCE_20260301_讲座_001',30,2,'现场扫码打卡成功','2026-09-08 21:48:57','2026-09-08 21:48:57',0),(4,3,1,'2026-09-08 16:48:57',NULL,NULL,NULL,NULL,NULL,0,1,'已成功报名，待打卡','2026-09-08 21:48:57','2026-09-08 21:48:57',0),(5,4,1,'2026-09-08 17:48:57',NULL,NULL,NULL,NULL,NULL,0,1,'已成功报名，待打卡','2026-09-08 21:48:57','2026-09-08 21:48:57',0),(6,6,7,'2026-09-09 09:32:34',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-09 09:32:34','2026-09-09 09:32:34',0),(7,3,7,'2026-09-09 09:32:35',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 09:32:34','2026-09-09 09:32:34',0),(8,3,8,'2026-09-09 09:33:33',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 09:33:32','2026-09-09 09:33:32',0),(9,6,9,'2026-09-09 09:34:13','2026-09-09 09:34:13',30.520001,114.360001,0,'NONCE_EF7ACCF8B8F148',1,2,NULL,'2026-09-09 09:34:12','2026-09-09 09:34:12',0),(10,6,1,'2026-09-09 10:39:21',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-09 10:39:20','2026-09-09 10:39:20',0),(13,1,3,'2026-09-09 15:46:50',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 11:13:41','2026-09-09 11:13:41',0),(14,1,6,'2026-09-09 15:41:25',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-09 11:31:16','2026-09-09 11:31:16',0),(15,1,9,'2026-09-09 15:22:32',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-09 11:32:19','2026-09-09 11:32:19',0),(18,1,108,'2026-09-09 16:33:33',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 14:41:11','2026-09-09 14:41:11',0),(19,1,110,'2026-09-08 14:41:11','2026-09-09 14:41:11',NULL,NULL,45,'NONCE_TEST_CASE_10',50,2,NULL,'2026-09-09 14:41:11','2026-09-09 14:41:11',0),(20,1,101,'2026-09-09 16:22:48',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 14:45:12','2026-09-09 14:45:12',0),(21,1,106,'2026-09-09 16:21:49',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-09 14:45:40','2026-09-09 14:45:40',0),(22,1,104,'2026-09-09 16:23:17',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-09 14:51:41','2026-09-09 14:51:41',0),(23,1,114514,'2026-09-11 09:46:49',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-11 09:07:15','2026-09-11 09:07:15',0),(24,6,114514,'2026-09-14 10:26:47',NULL,NULL,NULL,NULL,NULL,0,1,NULL,'2026-09-11 09:09:49','2026-09-11 09:09:49',0),(25,5,114514,'2026-09-11 09:47:25',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-11 09:16:17','2026-09-11 09:16:17',0),(26,1,114515,'2026-09-11 09:31:49',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-11 09:31:48','2026-09-11 09:31:48',0),(27,5,114515,'2026-09-11 09:32:59',NULL,NULL,NULL,NULL,NULL,0,3,NULL,'2026-09-11 09:31:54','2026-09-11 09:31:54',0),(28,6,114515,'2026-09-14 08:52:27','2026-09-14 10:44:51',24.625667,118.080470,489,'NONCE_C7C3A3F9D8BA4C',100,2,NULL,'2026-09-11 09:32:53','2026-09-11 09:32:53',0),(29,6,106,'2026-09-11 10:42:55','2026-09-11 10:51:03',30.524100,114.364200,0,'NONCE_8B62C06740F640',80,2,NULL,'2026-09-11 10:42:54','2026-09-11 10:42:54',0),(30,6,114516,'2026-09-11 11:00:08','2026-09-11 11:00:38',24.622770,118.085455,278,'NONCE_9E6793C208B947',500,2,NULL,'2026-09-11 11:00:07','2026-09-11 11:00:07',0)
ON DUPLICATE KEY UPDATE
    `student_id` = VALUES(`student_id`),
    `activity_id` = VALUES(`activity_id`),
    `signup_time` = VALUES(`signup_time`),
    `checkin_time` = VALUES(`checkin_time`),
    `checkin_latitude` = VALUES(`checkin_latitude`),
    `checkin_longitude` = VALUES(`checkin_longitude`),
    `checkin_distance` = VALUES(`checkin_distance`),
    `qr_nonce_used` = VALUES(`qr_nonce_used`),
    `points_awarded` = VALUES(`points_awarded`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 8. 学生礼品兑换订单数据 (student_gift)
-- -------------------------------------------------------------------
INSERT INTO `student_gift` (`id`, `order_no`, `student_id`, `gift_id`, `quantity`, `points_cost`, `receiver_name`, `receiver_phone`, `receiver_address`, `pickup_code`, `status`, `refund_points_status`, `delivery_time`, `cancel_time`, `cancel_reason`, `operator_id`, `remark`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'ORD202603010001',1,1,1,50,'张伟','13911110001','南区学生公寓12栋302室','PU20260301001',2,0,'2026-09-08 21:48:57',NULL,NULL,1,'已于一站式学生事务中心窗口核销领取','2026-09-08 21:48:57','2026-09-08 21:48:57',0),(2,'ORD202603010002',2,2,1,120,'王芳','13911110002','东区研究生宿舍5栋101室','PU20260301002',1,0,NULL,NULL,NULL,NULL,'待宿管站统一配送','2026-09-08 21:48:57','2026-09-08 21:48:57',0)
ON DUPLICATE KEY UPDATE
    `order_no` = VALUES(`order_no`),
    `student_id` = VALUES(`student_id`),
    `gift_id` = VALUES(`gift_id`),
    `quantity` = VALUES(`quantity`),
    `points_cost` = VALUES(`points_cost`),
    `receiver_name` = VALUES(`receiver_name`),
    `receiver_phone` = VALUES(`receiver_phone`),
    `receiver_address` = VALUES(`receiver_address`),
    `pickup_code` = VALUES(`pickup_code`),
    `status` = VALUES(`status`),
    `refund_points_status` = VALUES(`refund_points_status`),
    `delivery_time` = VALUES(`delivery_time`),
    `cancel_time` = VALUES(`cancel_time`),
    `cancel_reason` = VALUES(`cancel_reason`),
    `operator_id` = VALUES(`operator_id`),
    `remark` = VALUES(`remark`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 9. 学生积分变动流水台账数据 (student_point_record)
-- -------------------------------------------------------------------
INSERT INTO `student_point_record` (`id`, `student_id`, `type`, `change_points`, `before_points`, `after_points`, `description`, `ref_id`, `operator_id`, `create_time`, `deleted`)
VALUES
(1,1,1,15,0,15,'参与活动打卡奖励【2026春季校园晨跑健康打卡】',1,NULL,'2026-09-08 21:48:57',0),(2,1,1,30,15,45,'参与活动打卡奖励【“青年AI与新质生产力”前沿学术讲座】',3,NULL,'2026-09-08 21:48:57',0),(3,1,2,-50,230,180,'兑换积分商城礼品【定制校园帆布袋】',1,NULL,'2026-09-08 21:48:57',0),(4,2,1,15,265,280,'参与活动打卡奖励【2026春季校园晨跑健康打卡】',2,NULL,'2026-09-08 21:48:57',0),(5,3,3,50,60,110,'管理员考勤特别嘉奖加分',NULL,1,'2026-09-08 21:48:57',0),(6,6,1,300,0,300,'参与活动打卡奖励【“青年AI与新质生产力”前沿学术讲座】',2,NULL,'2026-03-01 10:30:00',0),(7,6,1,120,300,420,'参与活动打卡奖励【春季校园科技创新与程序设计大赛】',3,NULL,'2026-03-03 16:45:00',0),(8,6,2,-70,420,350,'兑换积分商城礼品【定制校园环保帆布袋套装】',1,NULL,'2026-03-05 14:20:00',0),(9,6,1,1,350,351,'活动打卡奖励',9,NULL,'2026-09-09 09:34:13',0),(10,1,1,15,180,195,'活动打卡奖励',1,NULL,'2026-09-09 16:39:32',0),(18,7,1,260,0,260,'参与活动打卡奖励【跨文化国际学术交流沙龙】',NULL,NULL,'2026-03-02 14:00:00',0),(19,8,1,380,0,380,'参与活动打卡奖励【全国大学生AI算法挑战赛校内选拔】',NULL,NULL,'2026-03-03 10:00:00',0),(20,8,2,-70,380,310,'兑换积分商城礼品【定制校园环保帆布袋套装】',NULL,NULL,'2026-03-06 15:30:00',0),(21,9,1,190,0,190,'参与活动打卡奖励【金融科技前沿与量化投资专题讲座】',NULL,NULL,'2026-03-04 16:20:00',0),(22,9,2,-50,190,140,'兑换积分商城礼品【文创精装记事本】',NULL,NULL,'2026-03-07 11:10:00',0),(23,10,1,150,0,150,'参与活动打卡奖励【校园网络安全攻防演练与实践】',NULL,NULL,'2026-03-05 09:30:00',0),(24,11,1,80,0,80,'参与活动打卡奖励【文创艺术设计与创意手作工作坊】',NULL,NULL,'2026-03-08 14:15:00',0),(25,6,1,80,351,431,'参与活动打卡奖励【【测试用例06】现场扫码打卡-开启GPS核验与颁发证书】',29,0,'2026-09-11 10:51:03',0),(26,6,1,500,431,931,'参与活动打卡奖励【面向ai编程】',30,0,'2026-09-11 11:00:38',0),(27,6,1,100,931,1031,'参与活动打卡奖励【检查并发用例】',28,0,'2026-09-14 10:44:50',0)
ON DUPLICATE KEY UPDATE
    `student_id` = VALUES(`student_id`),
    `type` = VALUES(`type`),
    `change_points` = VALUES(`change_points`),
    `before_points` = VALUES(`before_points`),
    `after_points` = VALUES(`after_points`),
    `description` = VALUES(`description`),
    `ref_id` = VALUES(`ref_id`),
    `operator_id` = VALUES(`operator_id`),
    `create_time` = VALUES(`create_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 10. 电子荣誉证书及验真数据 (certificate)
-- -------------------------------------------------------------------
INSERT INTO `certificate` (`id`, `certificate_no`, `student_id`, `activity_id`, `title`, `issuer`, `issue_time`, `file_url`, `status`, `revoke_reason`, `revoked_by`, `revoked_time`, `verify_count`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'CERT-2026-ACT2-A89F3B12',1,2,'青年AI前沿学术讲座结业证书','校科技创新协会','2026-09-08 21:48:57','https://images.unsplash.com/photo-1589330694653-dad6bc01cf0f?w=600',1,NULL,NULL,NULL,22,'2026-09-08 21:48:57','2026-09-11 11:06:20',0),(2,'CERT-106-6-17307E1C',6,106,'网络安全攻防实训结业证书','校网络空间安全学院 / 科技协会','2026-09-11 10:51:03',NULL,1,NULL,NULL,NULL,9,'2026-09-11 10:51:03','2026-09-11 11:19:58',0),(3,'CERT-114516-6-6EF1DD1C',6,114516,'面向ai编程证书','ai发展协会','2026-09-11 11:00:38',NULL,2,'查验不通过',1,'2026-09-11 11:04:07',46,'2026-09-11 11:00:38','2026-09-11 11:19:52',0)
ON DUPLICATE KEY UPDATE
    `certificate_no` = VALUES(`certificate_no`),
    `student_id` = VALUES(`student_id`),
    `activity_id` = VALUES(`activity_id`),
    `title` = VALUES(`title`),
    `issuer` = VALUES(`issuer`),
    `issue_time` = VALUES(`issue_time`),
    `file_url` = VALUES(`file_url`),
    `status` = VALUES(`status`),
    `revoke_reason` = VALUES(`revoke_reason`),
    `revoked_by` = VALUES(`revoked_by`),
    `revoked_time` = VALUES(`revoked_time`),
    `verify_count` = VALUES(`verify_count`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 11. 站内通知与系统消息数据 (sys_notice)
-- -------------------------------------------------------------------
INSERT INTO `sys_notice` (`id`, `receiver_type`, `receiver_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`, `deleted`)
VALUES
(1,2,1,'new_activity','新活动发布提醒','“2026春季校园晨跑健康打卡”已开放报名，快来参与获取积分吧！',1,1,NULL,'2026-09-07 21:48:57',0),(2,1,1,'gift_exchange','学生礼品兑换申请待处理','学生 张伟 申请兑换【校园定制环保帆布袋】，订单号: ORD202603010001',1,1,NULL,'2026-09-08 16:48:57',0),(3,2,1,'gift_send','礼品已核销发放通知','您兑换的【校园定制环保帆布袋】已成功核销发放，感谢您的积极参与！',1,0,NULL,'2026-09-08 21:48:57',0),(4,2,5,'waitlist_promoted','活动候补递补成功通知','您候补报名的“青年AI与新质生产力”学术讲座已有正式名额空出，您已成功递补转正！',2,0,NULL,'2026-09-08 21:48:57',0),(5,2,6,'activity_checkin_reward','🎉 活动打卡奖励到账','参与活动【【测试用例06】现场扫码打卡-开启GPS核验与颁发证书】打卡成功！奖励到账：+80 分，当前可用余额：431 分',29,1,'2026-09-11 10:53:59','2026-09-11 10:51:03',1),(6,2,6,'activity_checkin_reward','🎉 活动打卡奖励到账','参与活动【面向ai编程】打卡成功！奖励到账：+500 分，当前可用余额：931 分',30,1,'2026-09-11 11:04:34','2026-09-11 11:00:38',0),(7,2,6,'activity_checkin_reward','🎉 活动打卡奖励到账','参与活动【检查并发用例】打卡成功！奖励到账：+100 分，当前可用余额：1031 分',28,1,'2026-09-14 10:47:59','2026-09-14 10:44:51',0)
ON DUPLICATE KEY UPDATE
    `receiver_type` = VALUES(`receiver_type`),
    `receiver_id` = VALUES(`receiver_id`),
    `type` = VALUES(`type`),
    `title` = VALUES(`title`),
    `content` = VALUES(`content`),
    `biz_id` = VALUES(`biz_id`),
    `is_read` = VALUES(`is_read`),
    `read_time` = VALUES(`read_time`),
    `create_time` = VALUES(`create_time`),
    `deleted` = VALUES(`deleted`);

-- -------------------------------------------------------------------
-- 12. AI 智能问答知识库数据 (qa_knowledge)
-- -------------------------------------------------------------------
INSERT INTO `qa_knowledge` (`id`, `category`, `question`, `answer`, `keywords`, `status`, `sort_order`, `view_count`, `create_time`, `update_time`, `deleted`)
VALUES
(1,'SCORE','如何获得积分？积分有什么用？','在校园活动积分平台中，您可以通过报名参加学校各类社团、学术讲座、文体志愿活动，并在活动现场使用微信扫一扫或手机端扫描动态二维码完成打卡获得对应积分。累积的积分可用于在【积分商城】免费兑换精美文创、电子数码、生活用品及专属权益。','积分,获得,打卡,兑换',1,1,120,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(2,'ACTIVITY','为什么扫码打卡提示“超出打卡范围”或“打卡失败”？','部分活动开启了GPS定位范围核验。请确保：1. 手机已开启定位权限并允许浏览器获取地理位置；2. 您本人处于活动指定的范围（如体育场内300米半径）内；3. 二维码处于刷新有效期内。','打卡失败,定位,范围,打卡无效',1,2,85,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(3,'ACTIVITY','活动报名人数已满怎么办？','如果活动开启了候补排队机制，名额满员后您可以点击报名进入“候补队列”。当有正式报名的同学取消或被管理员调整时，系统将按候补排队顺序自动为您递补转正，并通过系统通知提醒您。','候补,排队,名额已满,报名',1,3,64,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(4,'GIFT','礼品兑换后如何领取？可以取消吗？','兑换成功后，您可以在【个人中心】-【我的兑换】中查看提货核销码（Pickup Code）。请凭提货码前往指定地点（如一站式学生事务中心或联系辅导员）线下核销提货。在管理员确认发放前，如需取消可联系管理员处理，取消后积分将自动退回。','礼品,领取,提货码,退还积分',1,4,98,'2026-09-08 21:48:57','2026-09-08 21:48:57',0),(5,'RULE','电子荣誉证书在哪里查看？如何验证真伪？','对于设置了证书的活动，打卡完成后系统将自动为您颁发电子荣誉证书，您可在【个人中心】-【我的证书】随时查看与下载。每张证书拥有全国唯一的防伪编码与验真二维码，扫码即可核验证书真实性。','证书,真伪,下载,荣誉',1,5,42,'2026-09-08 21:48:57','2026-09-08 21:48:57',0)
ON DUPLICATE KEY UPDATE
    `category` = VALUES(`category`),
    `question` = VALUES(`question`),
    `answer` = VALUES(`answer`),
    `keywords` = VALUES(`keywords`),
    `status` = VALUES(`status`),
    `sort_order` = VALUES(`sort_order`),
    `view_count` = VALUES(`view_count`),
    `create_time` = VALUES(`create_time`),
    `update_time` = VALUES(`update_time`),
    `deleted` = VALUES(`deleted`);

SET FOREIGN_KEY_CHECKS = 1;
