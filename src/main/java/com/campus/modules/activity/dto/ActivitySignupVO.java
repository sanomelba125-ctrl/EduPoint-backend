package com.campus.modules.activity.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivitySignupVO {
    private String status;
    private boolean waitlist;
    private Integer queueNumber;
    private String message;
}
