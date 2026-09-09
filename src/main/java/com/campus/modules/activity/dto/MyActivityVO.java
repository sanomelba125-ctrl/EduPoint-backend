package com.campus.modules.activity.dto;

import com.campus.modules.activity.entity.StudentActivity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生“我的活动”记录，统一承载正式报名、已完成、已失效和候补记录。
 * 候补记录的基础记录来自 activity_waitlist，其 status 固定映射为 4。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MyActivityVO extends StudentActivity {
    private Integer queueNumber;
    private String title;
    private String category;
    private String categoryName;
    private String coverImage;
    private Integer points;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String locationName;
    private String locationAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer locationRadius;
    private Integer locationCheckEnabled;
    private Integer certificateEnabled;
    private String certificateTitle;
}
