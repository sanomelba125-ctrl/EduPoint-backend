package com.campus.modules.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.dto.QrCodeVO;
import com.campus.modules.activity.entity.Activity;

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

    /**
     * 生成/刷新活动动态签到二维码（管理员专用）。
     * 调用时会失效该活动的所有当前有效码，重新生成并持久化一条新记录。
     *
     * @param activityId 活动ID
     * @return 新二维码 VO，含 nonce/sign/expireTime/qrContent
     */
    QrCodeVO generateQrCode(Long activityId);
}
