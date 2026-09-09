package com.campus.modules.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.activity.entity.ActivityCheckinCode;

/** Persistence operations for dynamic activity check-in codes. */
public interface ActivityCheckinCodeService extends IService<ActivityCheckinCode> {

    void invalidateActiveCodes(Long activityId);
}
