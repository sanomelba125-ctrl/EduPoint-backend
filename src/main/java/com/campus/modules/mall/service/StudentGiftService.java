package com.campus.modules.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.entity.StudentGift;

/**
 * 学生礼品兑换订单业务接口
 */
public interface StudentGiftService extends IService<StudentGift> {

    /**
     * 学生进入礼品详情页时获取防重兑换令牌 (P2-2 治理升级)
     */
    GiftExchangeTokenVO getExchangeToken(Long giftId, Long studentId);

    /**
     * 学生发起礼品兑换（原子防超卖、扣减积分、签发提货码）
     */
    GiftExchangeVO exchangeGift(GiftExchangeDTO dto, Long studentId);

    /**
     * 学生查询本人兑换订单列表
     */
    Page<StudentGiftVO> getMyOrders(Integer page, Integer size, Integer status, Long studentId);

    /**
     * 学生自主取消待提货订单（退还积分）
     */
    void studentCancelOrder(Long orderId, Long studentId);

    /**
     * 管理端全量查询兑换订单列表
     */
    Page<StudentGiftVO> getAdminOrderPage(GiftOrderQueryDTO queryDTO);

    /**
     * 管理端核销提货码并确认发放
     */
    void confirmDelivery(DeliveryConfirmDTO dto, Long adminId);

    /**
     * 管理端取消兑换订单（支持退款）
     */
    void adminCancelOrder(OrderCancelDTO dto, Long adminId);

    /**
     * 超时未提货订单自动关单与退还积分（由延时死信队列消费调用，具备严格幂等性）
     */
    void expireOrder(Long orderId);

    /**
     * 管理端获取礼品兑换订单全局业务统计指标
     */
    GiftOrderMetricsVO getOrderMetrics();

    /**
     * 学生端获取礼品动态防伪提货二维码
     *
     * @param orderId   订单ID
     * @param studentId 学生ID
     * @return 包含 Base64 图片及防伪 Token 的 VO
     */
    GiftPickupQrCodeVO getPickupQrCode(Long orderId, Long studentId);

    /**
     * 管理端/扫码枪扫码秒级核验并完成发货闭环
     *
     * @param dto     二维码内容及工作人员备注
     * @param adminId 操作人管理员ID
     * @return 核销成功结果
     */
    PickupVerifyResultVO verifyAndDeliverByQrCode(PickupQrVerifyDTO dto, Long adminId);

    /**
     * 秒杀高并发下单 (P1-1 & P1-2 同步段: Redis原子预扣 + 事务性发件箱 + MQ异步削峰)
     */
    GiftExchangeVO seckillExchange(GiftExchangeDTO dto, Long studentId);

    /**
     * 消费者异步落库处理 (P1-2 异步段: 数据库事务更新库存、扣减积分与保存订单)
     */
    boolean processAsyncOrder(OrderCreateMessage msg);

    /**
     * 按 requestId 查询秒杀订单处理结果，供前端轮询排队状态 (防横向越权)。
     *
     * <p>返回三态而非「有/无订单」：命中订单为 SUCCESS；查不到订单但存在失败标记为 FAILED；
     * 两者都没有才是 PROCESSING。缺了 FAILED 这一态，业务校验失败或消息判死的订单会让客户端
     * 永久停在「排队中」，用户既拿不到结果也无法重试。
     *
     * @param requestId 秒杀请求的幂等标识
     * @param studentId 当前登录学生 ID
     * @return 处理结果，永不为 null
     */
    SeckillResultVO getSeckillResultByRequestId(String requestId, Long studentId);

    /**
     * 记录一次秒杀处理失败，供轮询接口向客户端返回明确的失败原因。
     *
     * <p>只在「已确定不会落库」时调用（库存/积分/账号校验未通过、投递判死）；
     * 订单是否真的落库尚不确定时不写，避免把可能成功的订单误报为失败。
     *
     * @param studentId 学生 ID
     * @param requestId 秒杀请求的幂等标识
     * @param reason    面向用户的失败原因
     */
    void markSeckillFailed(Long studentId, String requestId, String reason);

    /**
     * 读取秒杀失败原因，用于轮询接口与诊断。
     *
     * @return 失败原因；不存在失败标记时返回 null
     */
    String getSeckillFailureReason(Long studentId, String requestId);

    /**
     * 判断指定学生的 requestId 是否已经落单。
     * 供 MQ 消费端在事务异常边界确认「订单是否其实已提交」，避免误回补库存。
     */
    boolean existsOrderByRequestId(Long studentId, String requestId);
}
