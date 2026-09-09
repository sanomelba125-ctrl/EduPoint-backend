package com.campus.modules.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ActivityCheckinDTO {
    @NotNull private Long activityId;
    @NotBlank private String nonce;
    @NotBlank private String sign;
    private BigDecimal checkinLatitude;
    private BigDecimal checkinLongitude;
}
