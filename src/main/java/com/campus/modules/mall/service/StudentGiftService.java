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
}
