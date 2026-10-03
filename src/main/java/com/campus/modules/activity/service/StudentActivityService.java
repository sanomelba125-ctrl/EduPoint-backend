package com.campus.modules.activity.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.modules.activity.dto.*;

public interface StudentActivityService {
    ActivitySignupVO signup(ActivitySignupDTO dto);
    String cancelSignup(Long activityId);
    ActivityCheckinVO checkin(ActivityCheckinDTO dto);
    IPage<MyActivityVO> myList(Integer page, Integer size, Integer status);
    IPage<ActivityRecordVO> adminList(Integer page, Integer size, String studentName, String studentNo, String activityTitle, Integer status);
    ActivityRecordMetricsVO getRecordMetrics();
}
