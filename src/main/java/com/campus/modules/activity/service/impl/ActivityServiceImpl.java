package com.campus.modules.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.config.ActivityQrCodeProperties;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.dto.QrCodeVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.ActivityCheckinCodeService;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.activity.service.ActivityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Activity 服务实现类
 * 负责具体业务逻辑实现
 */
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements ActivityService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ActivityCheckinCodeService checkinCodeService;

    @Resource
    private ActivityQrCodeProperties qrCodeProperties;

    @Resource
    private ActivityCacheService activityCacheService;

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
        activityCacheService.evict(activity.getId());
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
        activityCacheService.evict(activity.getId());
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
        activityCacheService.evict(activity.getId());
    }

    @Override
    public Activity detail(Long id) {
        Activity activity = activityCacheService.getDetail(id);
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
        }
        return activity;
    }

    @Override
    public IPage<Activity> page(ActivityPageQuery query) {
        long current = Math.max(query.getPage(), 1);
        long size = Math.min(Math.max(query.getSize(), 1), 100);
        String keyword = query.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        return lambdaQuery()
                .like(hasKeyword, Activity::getTitle, hasKeyword ? keyword.trim() : null)
                .eq(query.getCategory() != null && !query.getCategory().isBlank(), Activity::getCategory, query.getCategory())
                .eq(query.getStatus() != null, Activity::getStatus, query.getStatus())
                .orderByDesc(Activity::getStartTime)
                .page(new Page<>(current, size));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QrCodeVO generateQrCode(Long activityId) {
        // 1. 鉴权：必须是已登录的管理员
        requireAdminId();

        // 2. 校验活动是否存在
        Activity activity = getById(activityId);
        if (activity == null || Objects.equals(activity.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
        }
        if (Objects.equals(activity.getStatus(), 2) || Objects.equals(activity.getStatus(), 3)) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "已结束或已归档的活动不能生成签到码");
        }

        // 3. 失效该活动的所有当前有效码（status=0 → 2）
        checkinCodeService.invalidateActiveCodes(activityId);

        // 4. 生成新的 nonce（UUID去横线，大写）
        String nonce = "NONCE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();

        // 5. 生成 HMAC-SHA256 签名：sign = HmacSHA256(activityId + ":" + nonce, SIGN_SECRET)
        String sign = hmacSha256(activityId + ":" + nonce, qrCodeProperties.getSignSecret());
        String signToken = "SIGN_" + sign.substring(0, 18).toUpperCase();

        // 6. 计算过期时间
        int validSeconds = qrCodeProperties.getValidSeconds();
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(validSeconds);

        // 7. 持久化到 activity_checkin_code 表
        ActivityCheckinCode code = new ActivityCheckinCode();
        code.setActivityId(activityId);
        code.setNonce(nonce);
        code.setSign(signToken);
        code.setExpireTime(expireTime);
        code.setStatus(0);
        checkinCodeService.save(code);

        // 8. 拼装扫码跳转 URL
        String qrContent = qrCodeProperties.getCheckinBaseUrl()
                + "?actId=" + activityId
                + "&nonce=" + nonce
                + "&sign=" + signToken;

        return QrCodeVO.builder()
                .activityId(activityId)
                .nonce(nonce)
                .sign(signToken)
                .expireTime(expireTime.format(DATETIME_FMT))
                .validSeconds(validSeconds)
                .qrContent(qrContent)
                .build();
    }

    // ────────── 内部工具方法 ──────────

    /**
     * HMAC-SHA256 签名并返回 Base64-URL 编码（去除 = 填充）
     */
    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "签名生成失败：" + e.getMessage());
        }
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
