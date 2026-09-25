package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 礼品动态提货二维码响应 VO
 * 学生在兑换详情页展示该码供扫码枪核销
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftPickupQrCodeVO {

    /** 订单ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 礼品名称 */
    private String giftName;

    /** 兜底人工核验提货数字码（展示于二维码下方） */
    private String pickupCode;

    /** 动态提货防伪 Token 串（即二维码所载内容） */
    private String pickupToken;

    /** 二维码图片 Base64 Data URL (data:image/png;base64,...)，前端直接渲染 */
    private String qrCodeBase64;

    /** 二维码过期截止时间（格式：yyyy-MM-dd HH:mm:ss） */
    private String expireTime;

    /** 有效倒计时秒数 */
    private Integer validSeconds;
}
