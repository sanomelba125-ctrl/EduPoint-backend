package com.campus.modules.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;

/**
 * Activity 服务接口
 * 负责业务逻辑定义
 */
public interface ActivityService extends IService<Activity> {

    Activity create(ActivitySaveDTO dto);

    Activity update(ActivitySaveDTO dto);

    void archive(Long id);

    Activity detail(Long id);

    IPage<Activity> page(ActivityPageQuery query);
}
