package com.campus.modules.activity.dto;

import com.campus.modules.activity.entity.StudentActivity;
import lombok.Data;

@Data
public class ActivityRecordVO extends StudentActivity {
    private String studentName;
    private String studentNo;
    private String activityTitle;
}
