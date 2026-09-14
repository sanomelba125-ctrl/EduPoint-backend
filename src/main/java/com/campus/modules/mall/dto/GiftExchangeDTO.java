package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
