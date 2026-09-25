package com.campus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商城礼品提货码与核销动态二维码配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.pickup")
public class MallPickupProperties {

    /**
     * 提货动态二维码有效秒数，默认 300 秒（5 分钟）
     * 超时后需在学生端刷新，防止截图倒卖与重放核销
     */
    private int validSeconds = 300;

    /**
     * 提货二维码防伪防篡改 HMAC-SHA256 签名密钥
     */
    private String signSecret = "CampusMallPickupTokenSecretKey2026SafeGuard";
}
