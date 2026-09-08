package com.campus.modules.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.mapper.ActivityCheckinCodeMapper;
import com.campus.modules.activity.service.ActivityCheckinCodeService;
import org.springframework.stereotype.Service;

/** Default check-in code persistence service. */
@Service
public class ActivityCheckinCodeServiceImpl
        extends ServiceImpl<ActivityCheckinCodeMapper, ActivityCheckinCode>
        implements ActivityCheckinCodeService {

    @Override
    public void invalidateActiveCodes(Long activityId) {
        lambdaUpdate()
                .eq(ActivityCheckinCode::getActivityId, activityId)
                .eq(ActivityCheckinCode::getStatus, 0)
                .set(ActivityCheckinCode::getStatus, 2)
                .update();
    }
}
