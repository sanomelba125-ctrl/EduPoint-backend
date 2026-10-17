package com.campus.modules.mall.service;

import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.config.MallPickupProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 礼品动态提货防伪 Token 签发与验签服务
 * Token 结构：Base64Url(orderNo:pickupCode:timestamp:hmacSha256)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickupTokenService {

    private final MallPickupProperties mallPickupProperties;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickupTokenPayload {
        private String orderNo;
        private String pickupCode;
        private long timestamp;
    }

    /**
     * 为订单生成带有时间戳和 HMAC 防伪签名的提货 Token
     *
     * @param orderNo    订单号
     * @param pickupCode 原始数字提货码
     * @param timestamp  签发时间戳（毫秒）
     * @return Base64Url 编码的防伪 Token
     */
    public String generateToken(String orderNo, String pickupCode, long timestamp) {
        String secret = getSecret();
        String payload = orderNo + ":" + pickupCode + ":" + timestamp;
        String sign = hmacSha256(payload, secret);
        String fullRaw = payload + ":" + sign;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fullRaw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 扫码验签并解析 Token
     *
     * @param token 客户端/扫码枪扫出的 Base64 Token 字符串
     * @return 解析后的荷载信息
     */
    public PickupTokenPayload verifyAndParseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码内容不能为空");
        }

        String raw;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token.trim());
            raw = new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码格式无效");
        }

        String[] parts = raw.split(":");
        if (parts.length != 4) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码凭证结构损坏");
        }

        String orderNo = parts[0];
        String pickupCode = parts[1];
        long timestamp;
        try {
            timestamp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货凭据时间戳解析失败");
        }
        String receivedSign = parts[3];

        // 1. 校验动态码时效性（防截图与过期核销）
        long now = System.currentTimeMillis();
        long validDurationMillis = (long) mallPickupProperties.getValidSeconds() * 1000L;
        if (now - timestamp > validDurationMillis) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码已过期，请在学生端刷新后重试");
        }
        // 防止未来时间戳（偏差超过 60 秒）
        if (timestamp - now > 60000L) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货凭据时钟校验异常");
        }

        // 2. 恒定时间校验防伪签名 (MessageDigest.isEqual 防时序攻击)
        String payload = orderNo + ":" + pickupCode + ":" + timestamp;
        String expectedSign = hmacSha256(payload, getSecret());
        if (!MessageDigest.isEqual(expectedSign.getBytes(StandardCharsets.UTF_8),
                receivedSign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Pickup QR code tamper detected for orderNo={}, signature mismatch", orderNo);
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码防伪校验失败，无效凭证");
        }

        return PickupTokenPayload.builder()
                .orderNo(orderNo)
                .pickupCode(pickupCode)
                .timestamp(timestamp)
                .build();
    }

    private String getSecret() {
        if (mallPickupProperties == null || mallPickupProperties.getSignSecret() == null
                || mallPickupProperties.getSignSecret().isBlank()) {
            throw new IllegalStateException("提货码签名密钥未配置，请配置环境变量 MALL_PICKUP_SIGN_SECRET 或在配置文件中指定 mall.pickup.sign-secret");
        }
        return mallPickupProperties.getSignSecret();
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "HMAC签名计算异常");
        }
    }
}
