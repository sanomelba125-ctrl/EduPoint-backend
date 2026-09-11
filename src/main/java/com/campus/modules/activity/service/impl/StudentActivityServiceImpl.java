package com.campus.modules.activity.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.dto.*;
import com.campus.modules.activity.entity.*;
import com.campus.modules.activity.mapper.*;
import com.campus.modules.activity.service.StudentActivityService;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j @Service @RequiredArgsConstructor
public class StudentActivityServiceImpl extends ServiceImpl<StudentActivityMapper, StudentActivity> implements StudentActivityService {
    private final ActivityMapper activityMapper;
    private final ActivityWaitlistMapper waitlistMapper;
    private final ActivityCheckinCodeMapper codeMapper;
    private final StudentMapper studentMapper;
    private final StudentPointRecordMapper pointRecordMapper;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final CertificateService certificateService;
    private final ActivityCacheService activityCacheService;
    private final TransactionTemplate transactionTemplate;
    private static final long LOCK_WAIT_SECONDS = 5L;

    @PostConstruct
    void configurePublisherCallbacks() {
        rabbitTemplate.setConfirmCallback((correlation, confirmed, reason) -> {
            if (!confirmed) log.error("RabbitMQ publisher confirm failed: correlation={}, reason={}", correlation, reason);
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error("RabbitMQ returned an unroutable message: {}", returned));
    }

    @Override
    public ActivitySignupVO signup(ActivitySignupDTO dto) {
        Long sid = currentStudentId(); Long aid = dto.getActivityId();
        return withLock("activity:signup:" + aid, () -> transactionTemplate.execute(status -> {
            Activity a = activityMapper.selectById(aid);
            if (a == null || Objects.equals(a.getDeleted(),1)) throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "活动不存在");
            if (a.getStatus() == null || a.getStatus() != 0 && a.getStatus() != 1) throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动未开始或已结束");
            StudentActivity old = getOne(new LambdaQueryWrapper<StudentActivity>().eq(StudentActivity::getStudentId,sid).eq(StudentActivity::getActivityId,aid), false);
            if (old != null && old.getStatus() != 3) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "您已报名该活动");
            ActivityWaitlist waiting = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>().eq(ActivityWaitlist::getStudentId,sid).eq(ActivityWaitlist::getActivityId,aid));
            if (waiting != null && Objects.equals(waiting.getStatus(), 1)) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "您已在候补队列中");
            int reserved = reserveParticipant(a);
            if (reserved > 0) {
                StudentActivity rec = old == null ? new StudentActivity() : old;
                rec.setStudentId(sid); rec.setActivityId(aid); rec.setSignupTime(LocalDateTime.now()); rec.setStatus(1); rec.setPointsAwarded(0); rec.setDeleted(0);
                if (rec.getId() == null) save(rec); else updateById(rec);
                refreshActivityCacheAfterCommit(aid);
                return ActivitySignupVO.builder().status("ENROLLED").waitlist(false).message("您已成功正式报名！").build();
            }
            if (!Objects.equals(a.getWaitlistEnabled(),1)) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动报名名额已满");
            // 修复：仅统计当前处于候补中 (status=1 且 deleted=0) 的最大排队序号，避免退出后再进入时无限累加
            Integer max = waitlistMapper.selectList(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getActivityId, aid)
                    .eq(ActivityWaitlist::getStatus, 1)
                    .eq(ActivityWaitlist::getDeleted, 0))
                    .stream()
                    .map(ActivityWaitlist::getQueueNumber)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
            ActivityWaitlist w = waiting == null ? new ActivityWaitlist() : waiting;
            w.setActivityId(aid);
            w.setStudentId(sid);
            w.setQueueNumber(max + 1);
            w.setStatus(1);
            w.setWaitTime(LocalDateTime.now());
            w.setPromotedTime(null);
            w.setDeleted(0);
            if (w.getId() == null) waitlistMapper.insert(w); else waitlistMapper.updateById(w);

            // 动态计算当前排位顺位
            Long currentRank = waitlistMapper.selectCount(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getActivityId, aid)
                    .eq(ActivityWaitlist::getStatus, 1)
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
        return withLock("activity:signup:" + activityId, () -> transactionTemplate.execute(status -> {
            StudentActivity rec = getOne(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, sid)
                    .eq(StudentActivity::getActivityId, activityId)
                    .eq(StudentActivity::getStatus, 1));
            if (rec != null) {
                rec.setStatus(3);
                updateById(rec);
                activityMapper.decrementParticipant(activityId);
                promote(activityId);
                refreshActivityCacheAfterCommit(activityId);
                return "已取消报名";
            }
            ActivityWaitlist w = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getStudentId, sid)
                    .eq(ActivityWaitlist::getActivityId, activityId)
                    .eq(ActivityWaitlist::getStatus, 1)
                    .eq(ActivityWaitlist::getDeleted, 0));
            if (w != null) {
                w.setStatus(3);
                waitlistMapper.updateById(w);
                refreshActivityCacheAfterCommit(activityId);
                return "已退出候补队列";
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "未找到有效报名记录");
        }));
    }

    private void promote(Long aid) {
        ActivityWaitlist w = waitlistMapper.selectOne(new LambdaQueryWrapper<ActivityWaitlist>().eq(ActivityWaitlist::getActivityId,aid).eq(ActivityWaitlist::getStatus,1).orderByAsc(ActivityWaitlist::getQueueNumber).last("LIMIT 1"));
        Activity activity = activityMapper.selectById(aid);
        if (w == null || activity == null || reserveParticipant(activity) == 0) return;
        StudentActivity rec = getOne(new LambdaQueryWrapper<StudentActivity>().eq(StudentActivity::getStudentId, w.getStudentId()).eq(StudentActivity::getActivityId, aid), false);
        if (rec == null) rec = new StudentActivity();
        rec.setStudentId(w.getStudentId()); rec.setActivityId(aid); rec.setSignupTime(LocalDateTime.now()); rec.setStatus(1); rec.setPointsAwarded(0); rec.setDeleted(0);
        if (rec.getId() == null) save(rec); else updateById(rec);
        w.setStatus(2); w.setPromotedTime(LocalDateTime.now()); waitlistMapper.updateById(w);
    }

    @Override
    public ActivityCheckinVO checkin(ActivityCheckinDTO dto) {
        Long sid=currentStudentId(); Long aid=dto.getActivityId(); return withLock("activity:checkin:"+aid, () -> withLock("student:points:" + sid, () -> transactionTemplate.execute(status -> {
            Activity a=activityMapper.selectById(aid); if(a==null||a.getStatus()==null||a.getStatus()!=1) throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(),"活动未开始或已结束");
            StudentActivity rec=getOne(new LambdaQueryWrapper<StudentActivity>().eq(StudentActivity::getStudentId,sid).eq(StudentActivity::getActivityId,aid).eq(StudentActivity::getStatus,1)); if(rec==null) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"请先报名活动");
            ActivityCheckinCode code=codeMapper.selectOne(new LambdaQueryWrapper<ActivityCheckinCode>().eq(ActivityCheckinCode::getActivityId,aid).eq(ActivityCheckinCode::getNonce,dto.getNonce()).eq(ActivityCheckinCode::getSign,dto.getSign()).eq(ActivityCheckinCode::getStatus,0));
            if(code==null||code.getExpireTime()==null||code.getExpireTime().isBefore(LocalDateTime.now())) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"签到码无效或已过期");
            if(Objects.equals(a.getLocationCheckEnabled(),1)){ if(dto.getCheckinLatitude()==null||dto.getCheckinLongitude()==null) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"请提供定位信息"); int dist=distance(a.getLatitude(),a.getLongitude(),dto.getCheckinLatitude(),dto.getCheckinLongitude()); if(dist>(a.getLocationRadius()==null?300:a.getLocationRadius())) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"超出活动签到范围"); rec.setCheckinDistance(dist); }
            if (codeMapper.consume(code.getId(), sid) == 0) throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "签到码已被使用或已过期");
            int points=a.getPoints()==null?0:a.getPoints(); Student s=studentMapper.selectById(sid); int before=s.getCurrentPoints()==null?0:s.getCurrentPoints(); studentMapper.addCurrentPoints(sid,points); studentMapper.addTotalPoints(sid,points);
            LocalDateTime now=LocalDateTime.now(); rec.setStatus(2); rec.setCheckinTime(now); rec.setCheckinLatitude(dto.getCheckinLatitude()); rec.setCheckinLongitude(dto.getCheckinLongitude()); rec.setQrNonceUsed(dto.getNonce()); rec.setPointsAwarded(points); updateById(rec);
            StudentPointRecord pr=new StudentPointRecord(); pr.setStudentId(sid); pr.setType(1); pr.setChangePoints(points); pr.setBeforePoints(before); pr.setAfterPoints(before+points); pr.setDescription("活动打卡奖励"); pr.setRefId(rec.getId()); pointRecordMapper.insert(pr);
            publishAfterCommit(Map.of("studentId", sid, "activityId", aid, "recordId", rec.getId()));
            boolean issued=false;
            if (Objects.equals(a.getCertificateEnabled(),1)) { Certificate c=new Certificate(); c.setCertificateNo("CERT-"+aid+"-"+sid+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase()); c.setStudentId(sid); c.setActivityId(aid); c.setTitle(a.getCertificateTitle()); c.setIssuer(a.getCertificateIssuer()); c.setIssueTime(now); c.setStatus(1); c.setVerifyCount(0); c.setDeleted(0); certificateService.save(c); issued=true; }
            return ActivityCheckinVO.builder().pointsAwarded(points).currentPoints(before+points).certificateIssued(issued).checkinTime(now).build();
        })));
    }

    @Override
    public IPage<MyActivityVO> myList(Integer page, Integer size, Integer status) {
        Long studentId = currentStudentId();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);
        List<MyActivityVO> records = new ArrayList<>();

        if (status == null || status != 4) {
            List<StudentActivity> ownRecords = list(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, studentId)
                    .eq(status != null, StudentActivity::getStatus, status)
                    .orderByDesc(StudentActivity::getSignupTime));
            ownRecords.forEach(record -> records.add(toMyActivityVO(record, null)));
        }
        if (status == null || status == 4) {
            List<ActivityWaitlist> waitlists = waitlistMapper.selectList(new LambdaQueryWrapper<ActivityWaitlist>()
                    .eq(ActivityWaitlist::getStudentId, studentId)
                    .eq(ActivityWaitlist::getStatus, 1)
                    .eq(ActivityWaitlist::getDeleted, 0)
                    .orderByDesc(ActivityWaitlist::getWaitTime));
            waitlists.forEach(waitlist -> {
                Activity activity = activityMapper.selectById(waitlist.getActivityId());
                if (activity != null && !Objects.equals(activity.getDeleted(), 1)) {
                    records.add(toMyActivityVO(null, waitlist, activity));
                }
            });
        }

        records.sort(Comparator.comparing(this::activityRecordTime, Comparator.nullsLast(Comparator.reverseOrder())));
        long total = records.size();
        int from = (int) Math.min((current - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        Page<MyActivityVO> result = new Page<>(current, pageSize, total);
        result.setRecords(records.subList(from, to));
        return result;
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
            vo.setStatus(4);
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
    @Override public IPage<ActivityRecordVO> adminList(Integer page, Integer size, String studentName, String studentNo, String activityTitle, Integer status) {
        requireAdmin();
        long current = Math.max(page == null ? 1 : page, 1);
        long pageSize = Math.min(Math.max(size == null ? 10 : size, 1), 100);
        return baseMapper.selectAdminPage(new Page<>(current, pageSize), trim(studentName), trim(studentNo), trim(activityTitle), status);
    }

    private Long currentStudentId(){ StpUtil.checkLogin(); String id=StpUtil.getLoginIdAsString(); if(id==null||!id.startsWith("student:")) throw new BusinessException(ResultCode.FORBIDDEN.getCode(),"仅学生可操作"); try{return Long.valueOf(id.substring(8));}catch(Exception e){throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(),"登录状态无效");} }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void requireAdmin(){ StpUtil.checkLogin(); String id=StpUtil.getLoginIdAsString(); if(id==null||!id.startsWith("admin:")) throw new BusinessException(ResultCode.FORBIDDEN.getCode(),"仅管理员可操作"); }
    private int reserveParticipant(Activity activity) {
        Activity candidate = activity;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (activityMapper.incrementParticipant(candidate.getId(), candidate.getVersion()) > 0) return 1;
            Activity refreshed = activityMapper.selectById(candidate.getId());
            if (refreshed == null || !Objects.equals(refreshed.getDeleted(), 0)
                    || !Objects.equals(refreshed.getStatus(), 0) && !Objects.equals(refreshed.getStatus(), 1)) return 0;
            if (Objects.equals(refreshed.getVersion(), candidate.getVersion())) return 0;
            candidate = refreshed;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "报名人数更新冲突，请重试");
    }
    private void publishAfterCommit(Map<String, Object> message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.ACTIVITY_EXCHANGE,
                            RabbitMqConfig.ACTIVITY_CHECKIN_ROUTING_KEY, message);
                } catch (Exception e) {
                    // The persistent check-in record remains the source of truth for replay/reconciliation.
                    log.error("Post-commit check-in event publish failed: {}", message, e);
                }
            }
        });
    }
    private void refreshActivityCacheAfterCommit(Long activityId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { activityCacheService.requestRefresh(activityId); }
        });
    }
    private int distance(BigDecimal lat1,BigDecimal lon1,BigDecimal lat2,BigDecimal lon2){ if(lat1==null||lon1==null)return Integer.MAX_VALUE; double r=6371000, p=Math.PI/180, dLat=lat2.subtract(lat1).doubleValue()*p,dLon=lon2.subtract(lon1).doubleValue()*p; double x=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(lat1.doubleValue()*p)*Math.cos(lat2.doubleValue()*p)*Math.sin(dLon/2)*Math.sin(dLon/2); return (int)Math.round(2*r*Math.asin(Math.sqrt(x))); }
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
