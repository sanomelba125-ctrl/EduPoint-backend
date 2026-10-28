package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 秒杀订单处理结果（轮询接口返回体）。
 *
 * <p><b>为什么需要三态：</b>此前轮询接口只有「查到订单」和「查不到订单」两种结果，
 * 查不到就一律回「排队处理中」。可消息可能因为库存校验失败、账号冻结、积分不足或投递判死
 * 而<b>永远不会落库</b>，客户端便永久停在「排队中」，用户无法知道失败，只能反复重试。
 *
 * <p>现在明确区分三种终局：{@link #STATUS_PROCESSING} 继续等、
 * {@link #STATUS_SUCCESS} 展示提货码、{@link #STATUS_FAILED} 明确告知失败原因并恢复下单按钮。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO {

    /** 仍在排队处理中，尚未有终局。 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 订单已成功落库。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 已确定失败（库存/积分/账号/投递判死），不会再落库。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 处理状态：PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 失败原因，仅 status=FAILED 时有值，可直接展示给学生 */
    private String failReason;

    /** 订单 ID，落库成功后才有值 */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 提货码，落库成功后才有值 */
    private String pickupCode;

    /** 礼品 ID */
    private Long giftId;

    /** 礼品名称 */
    private String giftName;

    /** 兑换件数 */
    private Integer quantity;

    /** 消耗积分 */
    private Integer pointsCost;

    /** 落库后学生实时可用积分 */
    private Integer remainPoints;

    /** 订单创建时间 */
    private LocalDateTime createTime;
}
