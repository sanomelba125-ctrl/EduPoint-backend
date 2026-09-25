package com.campus.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Dynamic activity check-in QR code settings. */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "activity.qrcode")
public class ActivityQrCodeProperties {

    @Min(30)
    private int validSeconds = 300;

    @NotBlank(message = "活动签到二维码签名密钥未配置，请配置环境变量 ACTIVITY_QR_SIGN_SECRET 或在本地配置文件中指定 activity.qrcode.sign-secret")
    private String signSecret;

    @NotBlank
    private String checkinBaseUrl = "http://localhost:5173/#/checkin";
}
