package com.campus.mq;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 带发布确认关联的消息发送工具。
 *
 * <p>集中处理两件容易漏掉的事，避免每个调用点各写一遍还写错：
 * <ol>
 *   <li>给消息挂 {@link CorrelationData}，让 confirm 回调能按 messageId 找回发件箱记录；</li>
 *   <li>把 messageId 写进 AMQP header，让 basic.return 回调同样能定位到记录。</li>
 * </ol>
 *
 * <p><b>调用方不得在发送后自行把本地消息标成「已投递」</b> ——
 * 是否送达只能由 Broker 的 ack 决定，见 {@link PublishConfirmHandler}。
 */
public final class MqPublishSupport {

    private MqPublishSupport() {
    }

    /**
     * 发送消息并登记发布确认关联。
     *
     * @param template   RabbitTemplate
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param payload    消息体（强类型对象由 Jackson 序列化，避免二次编码）
     * @param messageId  发件箱消息 ID，同时作为确认关联 ID
     * @return 本次发送的确认关联对象（测试可用它断言）
     */
    public static CorrelationData publish(RabbitTemplate template,
                                          String exchange,
                                          String routingKey,
                                          Object payload,
                                          String messageId) {
        CorrelationData correlationData = new CorrelationData(messageId);
        template.convertAndSend(exchange, routingKey, payload, message -> {
            message.getMessageProperties().setHeader(PublishConfirmHandler.HEADER_MESSAGE_ID, messageId);
            return message;
        }, correlationData);
        return correlationData;
    }
}
