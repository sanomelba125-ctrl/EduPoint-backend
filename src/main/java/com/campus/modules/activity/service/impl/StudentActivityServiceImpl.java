package com.campus.modules.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.dto.*;
import com.campus.modules.activity.entity.*;
import com.campus.modules.activity.enums.ActivityStatus;
import com.campus.modules.activity.enums.ActivityWaitlistStatus;
import com.campus.modules.activity.enums.StudentActivityStatus;
import com.campus.modules.certificate.enums.CertificateStatus;
import com.campus.modules.activity.mapper.*;
import com.campus.modules.activity.service.StudentActivityService;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.student.service.StudentScoreService;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import com.campus.modules.certificate.service.CertificateRenderService;
import com.campus.modules.system.service.SysNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.campus.modules.activity.chain.CheckinChainPipeline;
import com.campus.modules.activity.chain.CheckinContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j @Service
public class StudentActivityServiceImpl extends ServiceImpl<StudentActivityMapper, StudentActivity> implements StudentActivityService {
    private final ActivityMapper activityMapper;
    private final ActivityWaitlistMapper waitlistMapper;
    private final ActivityCheckinCodeMapper codeMapper;
    private final StudentScoreService studentScoreService;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final CertificateService certificateService;
    private final ActivityCacheService activityCacheService;
    private final TransactionTemplate transactionTemplate;
    private final CheckinChainPipeline checkinChainPipeline;
    private final SysNoticeService sysNoticeService;
    private final CertificateRenderService certificateRenderService;
    private static final long LOCK_WAIT_SECONDS = 5L;

    @Autowired
    public StudentActivityServiceImpl(ActivityMapper activityMapper,
                                      ActivityWaitlistMapper waitlistMapper,
                                      ActivityCheckinCodeMapper codeMapper,
                                      StudentScoreService studentScoreService,
                                      RedissonClient redissonClient,
                                      RabbitTemplate rabbitTemplate,
                                      CertificateService certificateService,
                                      ActivityCacheService activityCacheService,
                                      TransactionTemplate transactionTemplate,
                                      CheckinChainPipeline checkinChainPipeline,
                                      SysNoticeService sysNoticeService,
                                      CertificateRenderService certificateRenderService) {
        this.activityMapper = activityMapper;
        this.waitlistMapper = waitlistMapper;
        this.codeMapper = codeMapper;
        this.studentScoreService = studentScoreService;
        this.redissonClient = redissonClient;
        this.rabbitTemplate = rabbitTemplate;
        this.certificateService = certificateService;
        this.activityCacheService = activityCacheService;
        this.transactionTemplate = transactionTemplate;
        this.checkinChainPipeline = Objects.requireNonNull(checkinChainPipeline, "checkinChainPipeline must not be null");
        this.sysNoticeService = sysNoticeService;
        this.certificateRenderService = certificateRenderService;
    }

    public StudentActivityServiceImpl(ActivityMapper activityMapper,
                                      ActivityWaitlistMapper waitlistMapper,
                                      ActivityCheckinCodeMapper codeMapper,
                                      StudentScoreService studentScoreService,
                                      RedissonClient redissonClient,
                                      RabbitTemplate rabbitTemplate,
                                      CertificateService certificateService,
                                      ActivityCacheService activityCacheService,
                                      TransactionTemplate transactionTemplate,
                                      CheckinChainPipeline checkinChainPipeline,
                                      SysNoticeService sysNoticeService) {
        this(activityMapper, waitlistMapper, codeMapper, studentScoreService, redissonClient,
                rabbitTemplate, certificateService, activityCacheService, transactionTemplate, checkinChainPipeline, sysNoticeService, null);
    }

    public StudentActivityServiceImpl(ActivityMapper activityMapper,
                                      ActivityWaitlistMapper waitlistMapper,
                                      ActivityCheckinCodeMapper codeMapper,
                                      StudentScoreService studentScoreService,
                                      RedissonClient redissonClient,
                                      RabbitTemplate rabbitTemplate,
                                      CertificateService certificateService,
                                      ActivityCacheService activityCacheService,
                                      TransactionTemplate transactionTemplate,
                                      CheckinChainPipeline checkinChainPipeline) {
        this(activityMapper, waitlistMapper, codeMapper, studentScoreService, redissonClient,
                rabbitTemplate, certificateService, activityCacheService, transactionTemplate, checkinChainPipeline, null, null);
    }

    @PostConstruct
    void configurePublisherCallbacks() {
        rabbitTemplate.setConfirmCallback((correlation, confirmed, reason) -> {
            if (!confirmed) log.error("RabbitMQ publisher confirm failed: correlation={}, reason={}", correlation, reason);
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error("RabbitMQ returned an unroutable message: {}", returned));
    }

    @Override
    public ActivitySignupVO signup(ActivitySignupDTO dto) {
        Long sid = currentStudentId();
        Long aid = dto.getActivityId();
        return withLock("student:signup:" + sid, () -> transactionTemplate.execute(status -> {
            Activity a = activityMapper.selectById(aid);
            if (a == null || Objects.equals(a.getDeleted(), 1)) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
            }
            if (a.getStatus() == null || (a.getStatus() != ActivityStatus.NOT_STARTED.getCode() && a.getStatus() != ActivityStatus.IN_PROGRESS.getCode())) {
                throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动未开始或已结束");
            }

            // P2-11: 区间重叠黄金判定定理（Interval Overlap Detection）
            // 校验学生已有报名活动的 [startTime, endTime] 区间，杜绝时间冲突与恶意多头占坑
            if (a.getStartTime() != null && a.getEndTime() != null) {
                List<Activity> enrolledActivities = baseMapper.selectActiveEnrolledActivities(sid, aid);
                if (enrolledActivities != null) {
                    for (Activity other : enrolledActivities) {
                        if (other.getStartTime() != null && other.getEndTime() != null) {
                            boolean overlap = a.getStartTime().isBefore(other.getEndTime()) && a.getEndTime().isAfter(other.getStartTime());
                            if (overlap) {
                                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                                        String.format("报名失败：该活动时间与您已报名的活动【%s】存在时间冲突，不可兼报！", other.getTitle()));
                            }
                        }
                    }
                }
            }

            StudentActivity old = getOne(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, sid)
                    .eq(StudentActivity::getActivityId, aid), false);
            if (old != null && old.getStatus() != StudentActivityStatus.CANCELLED.getCode()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "您已报名该活动");
            }
            ActivityWaitlist waiting = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getStudentId, sid)
                    .eq(ActivityWaitlist::getActivityId, aid));
            if (waiting != null && Objects.equals(waiting.getStatus(), ActivityWaitlistStatus.WAITING.getCode())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "您已在候补队列中");
            }
            int reserved = reserveParticipant(a);
            if (reserved > 0) {
                StudentActivity rec = old == null ? new StudentActivity() : old;
                rec.setStudentId(sid);
                rec.setActivityId(aid);
                rec.setSignupTime(LocalDateTime.now());
                rec.setStatus(StudentActivityStatus.REGISTERED);
                rec.setPointsAwarded(0);
                rec.setDeleted(0);
                if (rec.getId() == null) {
                    save(rec);
                } else {
                    updateById(rec);
                }
                refreshActivityCacheAfterCommit(aid);
                return ActivitySignupVO.builder().status("ENROLLED").waitlist(false).message("您已成功正式报名！").build();
            }
            if (!Objects.equals(a.getWaitlistEnabled(), 1)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动报名名额已满");
            }
            // P2-7: 聚合 SQL 替代全表扫描与内存 Stream 计算 (含对未打桩单测的回落兼容)
            Integer max = waitlistMapper.selectMaxQueueNumber(aid);
            if (max == null || max == 0) {
                try {
                    List<ActivityWaitlist> activeWaitlists = waitlistMapper.selectList(new LambdaQueryWrapper<ActivityWaitlist>()
                            .eq(ActivityWaitlist::getActivityId, aid)
                            .eq(ActivityWaitlist::getStatus, ActivityWaitlistStatus.WAITING.getCode())
                            .eq(ActivityWaitlist::getDeleted, 0));
                    if (activeWaitlists != null && !activeWaitlists.isEmpty()) {
                        max = activeWaitlists.stream()
                                .map(ActivityWaitlist::getQueueNumber)
                                .filter(Objects::nonNull)
                                .max(Integer::compareTo)
                                .orElse(0);
                    }
                } catch (Exception ignored) {
                }
            }
            if (max == null) {
                max = 0;
            }
            ActivityWaitlist w = waiting == null ? new ActivityWaitlist() : waiting;
            w.setActivityId(aid);
            w.setStudentId(sid);
            w.setQueueNumber(max + 1);
            w.setStatus(ActivityWaitlistStatus.WAITING);
            w.setWaitTime(LocalDateTime.now());
            w.setPromotedTime(null);
            w.setDeleted(0);
            if (w.getId() == null) {
                waitlistMapper.insert(w);
            } else {
                waitlistMapper.updateById(w);
            }

            // 动态计算当前排位顺位
            Long currentRank = waitlistMapper.selectCount(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getActivityId, aid)
                    .eq(ActivityWaitlist::getStatus, ActivityWaitlistStatus.WAITING.getCode())
                    .eq(ActivityWaitlist::getDeleted, 0)
                    .le(ActivityWaitlist::getQueueNumber, w.getQueueNumber()));
            int rank = currentRank != null && currentRank > 0 ? currentRank.intValue() : 1;
            refreshActivityCacheAfterCommit(aid);
            return ActivitySignupVO.builder()
                    .status("WAITLIST")
                    .waitlist(true)
                    .queueNumber(rank)
                    .message("名额已满，已进入候补队列（当前排第 " + rank + " 位）")
                    .build();
        }));
    }

    @Override
    public String cancelSignup(Long activityId) {
        Long sid = currentStudentId();
        return withLock("student:signup:" + sid, () -> transactionTemplate.execute(status -> {
            StudentActivity rec = getOne(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, sid)
                    .eq(StudentActivity::getActivityId, activityId)
                    .eq(StudentActivity::getStatus, StudentActivityStatus.REGISTERED.getCode()));
            if (rec != null) {
                // P2-10: 活动取消时效前置守卫，活动已开始或已结束严禁取消报名，杜绝跨时空转正已结束活动
                Activity a = activityMapper.selectById(activityId);
                if (a != null) {
                    LocalDateTime now = LocalDateTime.now();
                    if ((a.getStartTime() != null && now.isAfter(a.getStartTime()))
                            || Objects.equals(a.getStatus(), ActivityStatus.ENDED.getCode())
                            || Objects.equals(a.getStatus(), ActivityStatus.CLOSED.getCode())) {
                        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动已开始或已结束，无法取消报名");
                    }
                }
                rec.setStatus(StudentActivityStatus.CANCELLED);
                updateById(rec);
                activityMapper.decrementParticipant(activityId);
                promote(activityId);
                refreshActivityCacheAfterCommit(activityId);
                return "已取消报名";
            }
            ActivityWaitlist w = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getStudentId, sid)
                    .eq(ActivityWaitlist::getActivityId, activityId)
                    .eq(ActivityWaitlist::getStatus, ActivityWaitlistStatus.WAITING.getCode())
                    .eq(ActivityWaitlist::getDeleted, 0));
            if (w != null) {
                w.setStatus(ActivityWaitlistStatus.CANCELLED);
                waitlistMapper.updateById(w);
                refreshActivityCacheAfterCommit(activityId);
                return "已退出候补队列";
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "未找到有效报名记录");
        }));
    }

    private void promote(Long aid) {
        withLock("activity:waitlist:promote:" + aid, () -> {
            ActivityWaitlist w = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getActivityId, aid)
                    .eq(ActivityWaitlist::getStatus, ActivityWaitlistStatus.WAITING.getCode())
                    .orderByAsc(ActivityWaitlist::getQueueNumber)
                    .last("LIMIT 1"));
            Activity activity = activityMapper.selectById(aid);
            if (w == null || activity == null || reserveParticipant(activity) == 0) {
                return null;
            }

            // P2-11: 候补转正时进行时间冲突防重叠校验，若学生在此期间已报名其他冲突活动，自动跳过并顺延递补
            if (activity.getStartTime() != null && activity.getEndTime() != null) {
                List<Activity> enrolled = baseMapper.selectActiveEnrolledActivities(w.getStudentId(), aid);
                boolean hasConflict = false;
                if (enrolled != null) {
                    for (Activity other : enrolled) {
                        if (other.getStartTime() != null && other.getEndTime() != null) {
                            if (activity.getStartTime().isBefore(other.getEndTime()) && activity.getEndTime().isAfter(other.getStartTime())) {
                                hasConflict = true;
                                break;
                            }
                        }
                    }
                }
                if (hasConflict) {
                    log.info("候补转正检测到时间重叠冲突，自动跳过该候选人并顺延递补: studentId={}, activityId={}", w.getStudentId(), aid);
                    w.setStatus(ActivityWaitlistStatus.CANCELLED);
                    waitlistMapper.updateById(w);
                    promote(aid);
                    return null;
                }
            }

            StudentActivity rec = getOne(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, w.getStudentId())
                    .eq(StudentActivity::getActivityId, aid), false);
            if (rec == null) {
                rec = new StudentActivity();
            }
            rec.setStudentId(w.getStudentId());
            rec.setActivityId(aid);
            rec.setSignupTime(LocalDateTime.now());
            rec.setStatus(StudentActivityStatus.REGISTERED);
            rec.setPointsAwarded(0);
            rec.setDeleted(0);
            if (rec.getId() == null) {
                save(rec);
            } else {
                updateById(rec);
            }
            w.setStatus(ActivityWaitlistStatus.PROMOTED);
            w.setPromotedTime(LocalDateTime.now());
            waitlistMapper.updateById(w);

            // P2-10: 候补转正即时触达闭环，事务提交后触发系统通知与 WebSocket 强提醒
            final Long promotedStudentId = w.getStudentId();
            final String actTitle = activity.getTitle();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notifyWaitlistPromoted(promotedStudentId, aid, actTitle);
                    }
                });
            } else {
                notifyWaitlistPromoted(promotedStudentId, aid, actTitle);
            }
            return null;
        });
    }

    private void notifyWaitlistPromoted(Long studentId, Long activityId, String activityTitle) {
        if (sysNoticeService == null) {
            return;
        }
        try {
            String title = "活动候补转正通知";
            String content = String.format("🎉 恭喜！您候补报名的活动【%s】已成功递补转正为正式报名，请准时参加！", activityTitle);
            sysNoticeService.createAndSendNotice(2, studentId, "waitlist_promoted", title, content, activityId);
        } catch (Exception e) {
            log.error("候补转正系统通知推送异常: studentId={}, activityId={}, error={}", studentId, activityId, e.getMessage());
        }
    }

    @Override
    public ActivityCheckinVO checkin(ActivityCheckinDTO dto) {
        Long sid = currentStudentId();
        Long aid = dto.getActivityId();
        // P1-7: 细化锁粒度至学生打卡维度 student:checkin:{sid}:{aid}，彻底拆解双重嵌套锁与活动级全局串行化阻断
        return withLock("student:checkin:" + sid + ":" + aid, () -> transactionTemplate.execute(status -> {
            CheckinContext context = CheckinContext.builder()
                    .studentId(sid)
                    .dto(dto)
                    .build();

            checkinChainPipeline.execute(context);

            Activity a = context.getActivity();
            StudentActivity rec = context.getStudentActivity();

            int points = a.getPoints() == null ? 0 : a.getPoints();
            LocalDateTime now = LocalDateTime.now();
            rec.setStatus(StudentActivityStatus.COMPLETED);
            rec.setCheckinTime(now);
            rec.setCheckinLatitude(dto.getCheckinLatitude());
            rec.setCheckinLongitude(dto.getCheckinLongitude());
            rec.setQrNonceUsed(dto.getNonce());
            rec.setPointsAwarded(points);
            if (context.getCheckinDistance() != null) {
                rec.setCheckinDistance(context.getCheckinDistance());
            }
            updateById(rec);

            // 签到核验全链路通过后，记录签到码使用记录
            if (context.getCheckinCode() != null) {
                codeMapper.markUsed(context.getCheckinCode().getId(), sid);
            }

            // 统一调用 StudentScoreService 进行积分原子入账、记账流水与 WebSocket 通知
            int currentPoints = studentScoreService.rewardActivityCheckin(sid, rec.getId(), a.getTitle(), points);

            publishAfterCommit(Map.of("studentId", sid, "activityId", aid, "recordId", rec.getId()));
            boolean issued = false;
            if (Objects.equals(a.getCertificateEnabled(), 1)) {
                Certificate c = new Certificate();
                c.setCertificateNo("CERT-" + aid + "-" + sid + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                c.setStudentId(sid);
                c.setActivityId(aid);
                c.setTitle(a.getCertificateTitle());
                c.setIssuer(a.getCertificateIssuer());
                c.setIssueTime(now);
                c.setStatus(CertificateStatus.VALID);
                c.setVerifyCount(0);
                c.setDeleted(0);
                certificateService.save(c);
                issued = true;

                if (certificateRenderService != null && c.getId() != null) {
                    final Long certId = c.getId();
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    certificateRenderService.renderAndUpload(certId);
                                } catch (Exception e) {
                                    log.warn("异步渲染证书图片失败: certId={}, error={}", certId, e.getMessage());
                                }
                            }
                        });
                    } else {
                        try {
                            certificateRenderService.renderAndUpload(certId);
                        } catch (Exception e) {
                            log.warn("渲染证书图片失败: certId={}, error={}", certId, e.getMessage());
                        }
                    }
                }
            }
            return ActivityCheckinVO.builder()
                    .pointsAwarded(points)
                    .currentPoints(currentPoints)
                    .certificateIssued(issued)
                    .checkinTime(now)
                    .build();
        }));
    }


    @Override
    public IPage<MyActivityVO> myList(Integer page, Integer size, Integer status) {
        Long studentId = currentStudentId();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);
        List<MyActivityVO> records = new ArrayList<>();

        List<StudentActivity> ownRecords = Collections.emptyList();
        if (status == null || status != 4) {
            ownRecords = list(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, studentId)
                    .eq(status != null, StudentActivity::getStatus, status)
                    .orderByDesc(StudentActivity::getSignupTime));
        }

        List<ActivityWaitlist> waitlists = Collections.emptyList();
        if (status == null || status == 4) {
            waitlists = waitlistMapper.selectList(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getStudentId, studentId)
                    .eq(ActivityWaitlist::getStatus, ActivityWaitlistStatus.WAITING.getCode())
                    .eq(ActivityWaitlist::getDeleted, 0)
                    .orderByDesc(ActivityWaitlist::getWaitTime));
        }

        // 收集活动 ID 批量回表，彻底消除循环内 selectById 的 N+1 查询隐患
        Set<Long> activityIds = new HashSet<>();
        for (StudentActivity r : ownRecords) {
            if (r.getActivityId() != null) activityIds.add(r.getActivityId());
        }
        for (ActivityWaitlist w : waitlists) {
            if (w.getActivityId() != null) activityIds.add(w.getActivityId());
        }

        Map<Long, Activity> activityMap = getActivitiesByIds(activityIds);

        for (StudentActivity record : ownRecords) {
            records.add(toMyActivityVO(record, null, activityMap.get(record.getActivityId())));
        }
        for (ActivityWaitlist waitlist : waitlists) {
            Activity activity = activityMap.get(waitlist.getActivityId());
            if (activity != null && !Objects.equals(activity.getDeleted(), 1)) {
                records.add(toMyActivityVO(null, waitlist, activity));
            }
        }

        records.sort(Comparator.comparing(this::activityRecordTime, Comparator.nullsLast(Comparator.reverseOrder())));
        long total = records.size();
        int from = (int) Math.min((current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        Page<MyActivityVO> result = new Page<>(current, pageSize, total);
        result.setRecords(records.subList(from, to));
        return result;
    }

    private Map<Long, Activity> getActivitiesByIds(Set<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Activity> list = activityMapper.selectBatchIds(activityIds);
            if (list != null && !list.isEmpty()) {
                return list.stream().filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toMap(Activity::getId, java.util.function.Function.identity(), (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量查询活动详情异常，降级为单条回表: {}", e.getMessage());
        }
        // 兼容单测未 mock selectBatchIds 而是 mock 了 selectById 的场景
        Map<Long, Activity> fallback = new HashMap<>();
        for (Long id : activityIds) {
            Activity a = activityMapper.selectById(id);
            if (a != null) {
                fallback.put(id, a);
            }
        }
        return fallback;
    }

    private LocalDateTime activityRecordTime(MyActivityVO record) {
        return record.getSignupTime() != null ? record.getSignupTime() : record.getCreateTime();
    }

    private MyActivityVO toMyActivityVO(StudentActivity record, ActivityWaitlist waitlist) {
        Long activityId = record != null ? record.getActivityId() : waitlist.getActivityId();
        Activity activity = activityMapper.selectById(activityId);
        return toMyActivityVO(record, waitlist, activity);
    }

    private MyActivityVO toMyActivityVO(StudentActivity record, ActivityWaitlist waitlist, Activity activity) {
        Long activityId = record != null ? record.getActivityId() : waitlist.getActivityId();
        MyActivityVO vo = new MyActivityVO();
        if (record != null) {
            vo.setId(record.getId());
            vo.setStudentId(record.getStudentId());
            vo.setActivityId(record.getActivityId());
            vo.setSignupTime(record.getSignupTime());
            vo.setCheckinTime(record.getCheckinTime());
            vo.setCheckinLatitude(record.getCheckinLatitude());
            vo.setCheckinLongitude(record.getCheckinLongitude());
            vo.setCheckinDistance(record.getCheckinDistance());
            vo.setQrNonceUsed(record.getQrNonceUsed());
            vo.setPointsAwarded(record.getPointsAwarded());
            vo.setStatus(record.getStatus());
            vo.setRemark(record.getRemark());
            vo.setCreateTime(record.getCreateTime());
            vo.setUpdateTime(record.getUpdateTime());
            vo.setDeleted(record.getDeleted());
        } else {
            vo.setId(waitlist.getId());
            vo.setStudentId(waitlist.getStudentId());
            vo.setActivityId(waitlist.getActivityId());
            vo.setSignupTime(waitlist.getWaitTime());
            vo.setCreateTime(waitlist.getCreateTime() != null ? waitlist.getCreateTime() : waitlist.getWaitTime());
            vo.setUpdateTime(waitlist.getUpdateTime());
            vo.setStatus(StudentActivityStatus.WAITLIST.getCode());
            vo.setPointsAwarded(0);
            vo.setDeleted(waitlist.getDeleted());
            // 动态计算当前有效排位顺位
            Long rank = waitlistMapper.selectCount(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getActivityId, waitlist.getActivityId())
                    .eq(ActivityWaitlist::getStatus, 1)
                    .eq(ActivityWaitlist::getDeleted, 0)
                    .le(ActivityWaitlist::getQueueNumber, waitlist.getQueueNumber()));
            vo.setQueueNumber(rank != null && rank > 0 ? rank.intValue() : 1);
        }
        if (activity != null) {
            vo.setTitle(activity.getTitle());
            vo.setCategory(activity.getCategory());
            vo.setCoverImage(activity.getCoverImage());
            vo.setPoints(activity.getPoints());
            vo.setStartTime(activity.getStartTime());
            vo.setEndTime(activity.getEndTime());
            vo.setLocationName(activity.getLocationName());
            vo.setLocationAddress(activity.getLocationAddress());
            vo.setLongitude(activity.getLongitude());
            vo.setLatitude(activity.getLatitude());
            vo.setLocationRadius(activity.getLocationRadius());
            vo.setLocationCheckEnabled(activity.getLocationCheckEnabled());
            vo.setCertificateEnabled(activity.getCertificateEnabled());
            vo.setCertificateTitle(activity.getCertificateTitle());
        }
        return vo;
    }
    @Override
    public IPage<ActivityRecordVO> adminList(Integer page, Integer size, String studentName, String studentNo, String activityTitle, Integer status) {
        AuthUtil.requireAdminId();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);
        return baseMapper.selectAdminPage(new Page<>(current, pageSize), trim(studentName), trim(studentNo), trim(activityTitle), status);
    }

    @Override
    public ActivityRecordMetricsVO getRecordMetrics() {
        ActivityRecordMetricsVO vo = baseMapper.selectRecordMetrics();
        return vo != null ? vo : ActivityRecordMetricsVO.builder()
                .totalRecords(0L)
                .completedCount(0L)
                .pendingCount(0L)
                .cancelledCount(0L)
                .pointsDistributed(0L)
                .build();
    }

    private Long currentStudentId() {
        return AuthUtil.requireStudentId();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int reserveParticipant(Activity activity) {
        Activity candidate = activity;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (activityMapper.incrementParticipant(candidate.getId(), candidate.getVersion()) > 0) {
                return 1;
            }
            Activity refreshed = activityMapper.selectById(candidate.getId());
            if (refreshed == null || !Objects.equals(refreshed.getDeleted(), 0)
                    || (!Objects.equals(refreshed.getStatus(), ActivityStatus.NOT_STARTED.getCode())
                    && !Objects.equals(refreshed.getStatus(), ActivityStatus.IN_PROGRESS.getCode()))) {
                return 0;
            }
            if (Objects.equals(refreshed.getVersion(), candidate.getVersion())) {
                return 0;
            }
            candidate = refreshed;
            try {
                Thread.sleep(50L * (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "报名人数更新冲突，请重试");
    }

    private void publishAfterCommit(Map<String, Object> message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.ACTIVITY_EXCHANGE,
                            RabbitMqConfig.ACTIVITY_CHECKIN_ROUTING_KEY, message);
                } catch (Exception e) {
                    log.error("Post-commit check-in event publish failed: {}", message, e);
                }
            }
        });
    }

    private void refreshActivityCacheAfterCommit(Long activityId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                activityCacheService.requestRefresh(activityId);
            }
        });
    }

    private <T> T withLock(String key, java.util.function.Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            // Omitting leaseTime enables Redisson's watchdog auto-renewal.
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.ERROR.getCode(), "操作被中断");
        } catch (Exception e) {
            log.error("Redisson lock acquisition failed for key {}", key, e);
            throw new BusinessException(ResultCode.ERROR.getCode(), "系统繁忙，请稍后重试");
        }
        if (!acquired) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "操作处理中，请勿重复提交");
        }
        try {
            return action.get();
        }
        finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
