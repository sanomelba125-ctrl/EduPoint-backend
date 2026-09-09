package com.campus.modules.activity.service;

import com.campus.modules.activity.entity.Activity;

public interface ActivityCacheService {
    Activity getDetail(Long activityId);
    void refresh(Long activityId);
    void requestRefresh(Long activityId);
    void evict(Long activityId);
}
