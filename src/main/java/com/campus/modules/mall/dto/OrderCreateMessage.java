package com.campus.modules.mall.dto;

import java.io.Serializable;

/**
 * 礼品兑换订单异步创建消息契约 (P1-2 Java 17 record 强类型不可变契约)
 * 替代传统裸 Map<String, Object>，彻底消除拼写错误、类型擦除与反序列化隐患
 */
public record OrderCreateMessage(
        String messageId,
        Long studentId,
        Long giftId,
        Integer quantity,
        String requestId,
        String exchangeToken,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        long timestamp,
        String traceId,
        String orderNo,
        Integer unitPoints
) implements Serializable {

    public OrderCreateMessage(
            String messageId,
            Long studentId,
            Long giftId,
            Integer quantity,
            String requestId,
            String exchangeToken,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            long timestamp,
            String traceId
    ) {
        this(messageId, studentId, giftId, quantity, requestId, exchangeToken,
                receiverName, receiverPhone, receiverAddress, timestamp, traceId, null, null);
    }

    public OrderCreateMessage(
            String messageId,
            Long studentId,
            Long giftId,
            Integer quantity,
            String requestId,
            String exchangeToken,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            long timestamp,
            String traceId,
            String orderNo
    ) {
        this(messageId, studentId, giftId, quantity, requestId, exchangeToken,
                receiverName, receiverPhone, receiverAddress, timestamp, traceId, orderNo, null);
    }

    public OrderCreateMessage {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        if (studentId == null) {
            throw new IllegalArgumentException("studentId 不能为空");
        }
        if (giftId == null) {
            throw new IllegalArgumentException("giftId 不能为空");
        }
        if (quantity == null || quantity <= 0) {
            quantity = 1;
        }
        if (timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }
    }
}
