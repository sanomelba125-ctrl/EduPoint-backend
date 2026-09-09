package com.campus.modules.activity.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class ActivityCheckinVO {
    private Integer pointsAwarded;
    private Integer currentPoints;
    private boolean certificateIssued;
    private LocalDateTime checkinTime;
}
