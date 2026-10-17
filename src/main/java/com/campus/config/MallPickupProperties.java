package com.campus.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 商城礼品提货码与核销动态二维码配置属性
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "mall.pickup")
public class MallPickupProperties {

    /**
     * 提货动态二维码有效秒数，默认 300 秒（5 分钟）
     * 超时后需在学生端刷新，防止截图倒卖与重放核销
     */
    @Min(30)
    private int validSeconds = 300;

    /**
     * 提货二维码防伪防篡改 HMAC-SHA256 签名密钥（生产严禁硬编码默认值，必须通过环境变量 MALL_PICKUP_SIGN_SECRET 注入）
     */
    @NotBlank(message = "提货码签名密钥未配置，请配置环境变量 MALL_PICKUP_SIGN_SECRET 或在配置文件中指定 mall.pickup.sign-secret")
    private String signSecret;
}
