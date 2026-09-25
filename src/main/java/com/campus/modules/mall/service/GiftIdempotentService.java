package com.campus.modules.mall.service;

import com.campus.modules.mall.dto.GiftExchangeTokenVO;
import com.campus.modules.mall.entity.StudentGift;

/**
 * 礼品兑换幂等与防重令牌专职服务
 * 职责：
 * 1. 服务端防重 Token 的生成与 Redis 存储 (TTL 10min)；
 * 2. 兑换时 Lua 脚本原子核验并删除 Token (一次性 Token 桶，核验即焚)；
 * 3. 并发重复点击 SETNX 槽位申请、事务回滚释放与完成标记；
 * 4. 严格基于 (student_id, request_id) 联合维度反查既有订单，对齐 Redis 与 DB 索引维度。
 */
public interface GiftIdempotentService {

    /**
     * 为指定学生和礼品生成防重兑换令牌 (进入详情页时调用)
     *
     * @param studentId 学生ID
     * @param giftId    礼品ID
     * @return 包含令牌与过期时长的 VO
     */
    GiftExchangeTokenVO generateExchangeToken(Long studentId, Long giftId);

    /**
     * 使用 Lua 脚本原子校验并销毁防重令牌
     *
     * @param studentId 学生ID
     * @param giftId    礼品ID
     * @param token     客户端回传的令牌
     * @return true-核验通过且已成功销毁; false-令牌不存在、已失效或不匹配
     */
    boolean verifyAndConsumeToken(Long studentId, Long giftId, String token);

    /**
     * 抢占并发防重槽位 (Redis SETNX)
     *
     * @param studentId 学生ID
     * @param requestId 幂等请求ID
     * @return true-抢占成功获得执行权; false-已有并发请求正在处理中
     */
    boolean tryAcquireSlot(Long studentId, String requestId);

    /**
     * 标记槽位处理完成 (事务成功提交后调用)
     *
     * @param studentId 学生ID
     * @param requestId 幂等请求ID
     * @param orderId   已落库订单ID
     */
    void markSlotDone(Long studentId, String requestId, Long orderId);

    /**
     * 事务回滚时释放槽位，允许重试
     *
     * @param studentId 学生ID
     * @param requestId 幂等请求ID
     */
    void releaseSlotOnRollback(Long studentId, String requestId);

    /**
     * 基于 (student_id, request_id) 联合维度反查既有订单
     *
     * @param studentId 学生ID
     * @param requestId 幂等请求ID
     * @return 既有订单或 null
     */
    StudentGift findExistingOrder(Long studentId, String requestId);
}
