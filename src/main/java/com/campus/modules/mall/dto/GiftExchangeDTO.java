package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 礼品兑换请求入参
 */
@Data
public class GiftExchangeDTO {

    /**
     * 客户端请求幂等标识（UUID）。
     * 同一次「兑换意图」内建议保持不变：弱网重试、用户手动重试都复用同一个标识，
     * 服务端据此保证同一标识只会落一单，重复提交直接返回原订单结果。
     * 若客户端未携带，服务端将自动补齐唯一标识降级处理，兼容存量/第三方调用。
     */
    @Size(max = 64, message = "请求幂等标识长度不能超过 64 位")
    private String requestId;

    /**
     * 服务端下发的防重兑换令牌 (P2-2 治理升级)。
     * 进入商品详情页由 GET /gift/{id}/token 动态下发，存入 Redis，TTL 10min；
     * 提交兑换时由服务端执行 Lua 脚本原子核验并销毁（一次性 Token 桶），防重进页面重复下单与重放攻击。
     */
    @Size(max = 128, message = "防重兑换令牌长度不能超过 128 位")
    private String exchangeToken;

    @NotNull(message = "礼品ID不能为空")
    private Long giftId;

    @NotNull(message = "兑换件数不能为空")
    @Min(value = 1, message = "兑换数量至少为 1 件")
    @Max(value = 5, message = "单次兑换上限为 5 件")
    private Integer quantity = 1;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;
}
