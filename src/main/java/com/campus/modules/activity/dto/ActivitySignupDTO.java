package com.campus.modules.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivitySignupDTO {
    @NotNull
    private Long activityId;
}
