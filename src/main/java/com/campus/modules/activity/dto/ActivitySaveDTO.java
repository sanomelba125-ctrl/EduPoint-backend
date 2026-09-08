package com.campus.modules.activity.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 活动创建和修改请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySaveDTO {

    private Long id;

    @NotBlank(message = "活动标题不能为空")
    private String title;

    private String category;
    private String description;
    private String ruleDescription;
    private String coverImage;

    @NotNull(message = "活动积分不能为空")
    @Min(value = 1, message = "活动积分必须大于0")
    private Integer points;

    @NotNull(message = "活动开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "活动结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "报名人数上限不能为空")
    @Min(value = 0, message = "报名人数上限不能小于0")
    private Integer registrationLimit;

    private Integer waitlistEnabled;
    private String locationName;
    private String locationAddress;

    @DecimalMin(value = "-180", message = "经度范围必须在-180到180之间")
    @DecimalMax(value = "180", message = "经度范围必须在-180到180之间")
    private BigDecimal longitude;

    @DecimalMin(value = "-90", message = "纬度范围必须在-90到90之间")
    @DecimalMax(value = "90", message = "纬度范围必须在-90到90之间")
    private BigDecimal latitude;

    @Min(value = 0, message = "签到半径不能小于0")
    private Integer locationRadius;

    private Integer locationCheckEnabled;
    private Integer certificateEnabled;
    private String certificateTitle;
    private String certificateIssuer;
}
