package com.campus.modules.activity.dto;

import com.campus.modules.activity.entity.StudentActivity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityRecordVO extends StudentActivity {
    private String studentName;
    private String studentNo;
    private String activityTitle;
}
