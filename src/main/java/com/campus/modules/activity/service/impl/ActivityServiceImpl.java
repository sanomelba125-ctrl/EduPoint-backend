package com.campus.modules.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.ActivityService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Activity 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements ActivityService {

    @Override
    public Activity create(ActivitySaveDTO dto) {
        Long adminId = requireAdminId();
        validateTime(dto);
        Activity activity = new Activity();
        copy(dto, activity);
        activity.setParticipantCount(0);
        activity.setStatus(resolveStatus(dto.getStartTime(), dto.getEndTime()));
        activity.setCreatedBy(adminId);
        activity.setUpdatedBy(adminId);
        activity.setDeleted(0);
        save(activity);
        return activity;
    }

    @Override
    public Activity update(ActivitySaveDTO dto) {
        Long adminId = requireAdminId();
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动ID不能为空");
        }
        validateTime(dto);
        Activity activity = getById(dto.getId());
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
        }
        copy(dto, activity);
        if (!Objects.equals(activity.getStatus(), 3)) {
            activity.setStatus(resolveStatus(dto.getStartTime(), dto.getEndTime()));
        }
        activity.setUpdatedBy(adminId);
        updateById(activity);
        return activity;
    }

    @Override
    public void archive(Long id) {
        Long adminId = requireAdminId();
        Activity activity = getById(id);
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
        }
        activity.setStatus(3);
        activity.setUpdatedBy(adminId);
        updateById(activity);
    }

    @Override
    public Activity detail(Long id) {
        Activity activity = getById(id);
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
        }
        return activity;
    }

    @Override
    public IPage<Activity> page(ActivityPageQuery query) {
        long current = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), 100);
        return lambdaQuery()
                .like(query.getTitle() != null && !query.getTitle().isBlank(), Activity::getTitle, query.getTitle())
                .eq(query.getCategory() != null && !query.getCategory().isBlank(), Activity::getCategory, query.getCategory())
                .eq(query.getStatus() != null, Activity::getStatus, query.getStatus())
                .orderByDesc(Activity::getStartTime)
                .page(new Page<>(current, size));
    }

    private Long requireAdminId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith("admin:")) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问管理员活动接口");
        }
        try {
            return Long.parseLong(loginId.substring("admin:".length()));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "管理员登录状态无效");
        }
    }

    private void validateTime(ActivitySaveDTO dto) {
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动结束时间不能早于开始时间");
        }
    }

    private Integer resolveStatus(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start)) return 0;
        if (now.isAfter(end)) return 2;
        return 1;
    }

    private void copy(ActivitySaveDTO dto, Activity activity) {
        activity.setTitle(dto.getTitle().trim());
        activity.setCategory(dto.getCategory() == null || dto.getCategory().isBlank() ? "OTHER" : dto.getCategory());
        activity.setDescription(dto.getDescription());
        activity.setRuleDescription(dto.getRuleDescription());
        activity.setCoverImage(dto.getCoverImage());
        activity.setPoints(dto.getPoints());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setRegistrationLimit(dto.getRegistrationLimit());
        activity.setWaitlistEnabled(dto.getWaitlistEnabled() == null ? 1 : dto.getWaitlistEnabled());
        activity.setLocationName(dto.getLocationName());
        activity.setLocationAddress(dto.getLocationAddress());
        activity.setLongitude(dto.getLongitude());
        activity.setLatitude(dto.getLatitude());
        activity.setLocationRadius(dto.getLocationRadius() == null ? 300 : dto.getLocationRadius());
        activity.setLocationCheckEnabled(dto.getLocationCheckEnabled() == null ? 0 : dto.getLocationCheckEnabled());
        activity.setCertificateEnabled(dto.getCertificateEnabled() == null ? 0 : dto.getCertificateEnabled());
        activity.setCertificateTitle(dto.getCertificateTitle());
        activity.setCertificateIssuer(dto.getCertificateIssuer());
    }
}
