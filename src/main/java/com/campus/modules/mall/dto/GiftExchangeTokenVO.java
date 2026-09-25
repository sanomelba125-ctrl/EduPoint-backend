package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 礼品兑换防重令牌响应 VO
 * 对应 P2-2 服务端下发防重 Token，用于详情页获取并在提交兑换时回传核验
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftExchangeTokenVO {

    /** 防重兑换令牌 (一次性凭据，Token 桶机制) */
    private String token;

    /** 关联礼品 ID */
    private Long giftId;

    /** 令牌有效剩余秒数 (默认 10 分钟 = 600 秒) */
    private Long expireSeconds;

    /** 令牌生成时间 */
    private LocalDateTime createTime;
}
