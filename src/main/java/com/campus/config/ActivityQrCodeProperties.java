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

    @NotBlank
    private String signSecret = "EduPoint@QrSign#2026";

    @NotBlank
    private String checkinBaseUrl = "http://localhost:5173/#/checkin";
}
