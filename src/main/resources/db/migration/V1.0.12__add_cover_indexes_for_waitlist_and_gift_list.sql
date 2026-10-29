-- ===================================================================
-- V1.0.12: 两处列表查询的覆盖索引（2026-10-29，对应面试风险清单 P0-3 / P1-7）
--
-- 背景与实测（数据量：activity_waitlist 3013 行、gift 2006 行，均 ANALYZE 后取 EXPLAIN）
--
-- ① activity_waitlist.selectMaxQueueNumber
--    SQL: SELECT COALESCE(MAX(queue_number),0) FROM activity_waitlist
--         WHERE activity_id=? AND status=1 AND deleted=0
--    旧索引 idx_act_queue(activity_id, status, queue_number) 不含 deleted，
--    优化器实际选择了 uk_act_stu_wait(activity_id, student_id)：
--        type=ref  key=uk_act_stu_wait  rows=1000  Extra=Using where   ← 每行都要回表判断 status/deleted
--    补上含 deleted 的覆盖索引后（保留 deleted=0 的语义，不改查询）：
--        Extra=Select tables optimized away                        ← 单次索引定位直取 MAX，完全不回表
--
--    为什么不是「删掉 SQL 里的 AND deleted = 0」：那样虽然同样能优化掉，但会把软删语义丢掉，
--    一旦将来候补记录被逻辑删除就会被算进排队号。加索引是等价优化且不改语义，更安全。
--
-- ② gift.getGiftPage 列表页（ORDER BY create_time DESC）
--    移动端固定传 status=1（gift/index.vue），查询为
--        WHERE deleted=0 AND status=1 ORDER BY create_time DESC LIMIT 10
--    旧索引 idx_status(status, deleted) 能命中但排序仍要额外排：
--        type=ref  key=idx_status  rows=1006  Extra=Using filesort
--    新建 (deleted, status, create_time) 后：
--        type=range  key=idx_gift_list  Extra=Using index condition; Backward index scan  ← 反向扫索引，无 filesort
--
--    已知边界：管理端列表不传 status（WHERE deleted=0 ORDER BY create_time DESC），
--    仍是全表扫描 + filesort。要彻底解决需再加 (deleted, create_time)，但实测加上之后
--    优化器会改选这条索引去服务移动端查询（Extra 多出 Using where，status 只能当过滤条件），
--    反而把热路径变差，故不添加；管理端 QPS 低、数据量小，全表扫描可接受。
-- ===================================================================

-- ① 候补排队号查询：把 deleted 纳入索引，形成真正的覆盖索引
ALTER TABLE `activity_waitlist`
    ADD INDEX `idx_act_queue_cover` (`activity_id`, `status`, `deleted`, `queue_number`);

-- 旧索引被新索引完全覆盖（前导列相同，且所有查询都带 deleted=0 等值条件），删除以免重复维护
ALTER TABLE `activity_waitlist`
    DROP INDEX `idx_act_queue`;

-- ② 礼品列表页：按「未删除 + 状态 + 创建时间」建索引，消除 filesort
ALTER TABLE `gift`
    ADD INDEX `idx_gift_list` (`deleted`, `status`, `create_time`);

-- 旧索引 (status, deleted) 被新索引取代（所有 gift 查询都经全局逻辑删除自动带 deleted=0）
ALTER TABLE `gift`
    DROP INDEX `idx_status`;
