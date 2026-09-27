package com.campus;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.dto.ActivityCheckinVO;
import com.campus.modules.activity.dto.ActivityRecordMetricsVO;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.dto.ActivitySignupDTO;
import com.campus.modules.activity.dto.ActivitySignupVO;
import com.campus.modules.activity.dto.MyActivityVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityCheckinCodeMapper;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.ActivityWaitlistMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.activity.service.impl.StudentActivityServiceImpl;
import com.campus.modules.activity.chain.ActivityStatusCheckinHandler;
import com.campus.modules.activity.chain.CheckinChainPipeline;
import com.campus.modules.activity.chain.DuplicateCheckinHandler;
import com.campus.modules.activity.chain.EnrollmentCheckinHandler;
import com.campus.modules.activity.chain.GeoFenceCheckinHandler;
import com.campus.modules.activity.chain.QrCodeHmacCheckinHandler;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import com.campus.modules.student.service.StudentScoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口 3 /student/activity 活动报名、候补与动态打卡模块 —— 服务层单元测试。
 *
 * 覆盖点：
 * 1. 报名三态：名额充足直接转正、名额满且开启候补入队并返回实时排位、名额满且未开候补拒绝
 * 2. 报名并发防护：Redisson 分布式锁互斥 + 活动 participant_count 乐观锁重试与失败兜底
 * 3. 取消报名：正式名额释放后自动递补候补队首，候补本人取消则直接退队
 * 4. 打卡七步校验链：活动进行中、已报名、签到码存在/未过期、防重放原子核销、GPS 围栏距离
 * 5. 打卡成功后积分入账、MQ 事件投递（事务提交后）与电子证书联动颁发
 * 6. 我的活动记录：正式记录与候补记录合并、按时间倒序、内存分页边界钳制
 */
@DisplayName("模块 3：活动报名/候补/打卡 (/student/activity) 服务层测试")
class StudentActivityServiceImplTest {

    private static final Long STUDENT_ID = 55L;
    private static final Long ACTIVITY_ID = 9L;
    private static final Long ADMIN_ID = 7L;
    private static final String NONCE = "NONCE_7A9F8B2C4D1E00";
    private static final String SIGN = "SIGN_9A8B7C6D5E4F3A2B1C";

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityWaitlistMapper waitlistMapper;
    @Mock
    private ActivityCheckinCodeMapper codeMapper;
    @Mock
    private StudentActivityMapper studentActivityMapper;
    @Mock
    private StudentScoreService studentScoreService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private CertificateService certificateService;
    @Mock
    private ActivityCacheService activityCacheService;
    @Mock
    private com.campus.modules.system.service.SysNoticeService sysNoticeService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private RLock lock;

    private AutoCloseable mocks;
    private MockedStatic<AuthUtil> authUtil;
    private StudentActivityServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        authUtil = mockStatic(AuthUtil.class);
        authUtil.when(AuthUtil::requireStudentId).thenReturn(STUDENT_ID);

        // 让事务模板在单测中真正执行事务回调体
        when(transactionTemplate.execute(ArgumentMatchers.<TransactionCallback<Object>>any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            TransactionStatus status = new SimpleTransactionStatus();
            return callback.doInTransaction(status);
        });

        // Redisson 分布式锁默认加锁成功
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // 模拟 Spring 事务同步处于激活状态，以便校验 afterCommit 语义
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }

        CheckinChainPipeline pipeline = new CheckinChainPipeline(List.of(
                new ActivityStatusCheckinHandler(activityMapper),
                new EnrollmentCheckinHandler(studentActivityMapper),
                new DuplicateCheckinHandler(),
                new QrCodeHmacCheckinHandler(codeMapper),
                new GeoFenceCheckinHandler()
        ));

        service = new StudentActivityServiceImpl(activityMapper, waitlistMapper, codeMapper,
                studentScoreService, redissonClient, rabbitTemplate, certificateService,
                activityCacheService, transactionTemplate, pipeline, sysNoticeService);
        ReflectionTestUtils.setField(service, "baseMapper", studentActivityMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        authUtil.close();
        mocks.close();
    }

    // ────────────────────────── 报名：名额充足 ──────────────────────────

    @Test
    @DisplayName("报名：名额充足时直接转正，落库状态 1 且缓存失效在事务提交后才触发")
    void signup_shouldEnrollWhenSeatIsAvailable() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(ACTIVITY_ID, 0)).thenReturn(1);
        when(studentActivityMapper.insert(any(StudentActivity.class))).thenAnswer(invocation -> {
            StudentActivity entity = invocation.getArgument(0);
            entity.setId(501L);
            return 1;
        });

        ActivitySignupVO result = service.signup(signupRequest(ACTIVITY_ID));

        assertEquals("ENROLLED", result.getStatus());
        assertFalse(result.isWaitlist());
        assertNull(result.getQueueNumber());

        ArgumentCaptor<StudentActivity> captor = ArgumentCaptor.forClass(StudentActivity.class);
        verify(studentActivityMapper).insert(captor.capture());
        StudentActivity saved = captor.getValue();
        assertEquals(STUDENT_ID, saved.getStudentId());
        assertEquals(ACTIVITY_ID, saved.getActivityId());
        assertEquals(1, saved.getStatus());
        assertEquals(0, saved.getPointsAwarded());
        assertEquals(0, saved.getDeleted());
        assertNotNull(saved.getSignupTime());

        // 事务提交前不得刷新缓存，避免读到未落库数据
        verify(activityCacheService, never()).requestRefresh(any());
        triggerAfterCommit();
        verify(activityCacheService).requestRefresh(ACTIVITY_ID);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("报名：活动不存在或已逻辑删除时返回 404")
    void signup_shouldRejectMissingActivity() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(404, missing.getCode());
        assertEquals("活动不存在", missing.getMessage());

        Activity deleted = activity(ACTIVITY_ID, 1, 0);
        deleted.setDeleted(1);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(deleted);
        BusinessException removed = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(404, removed.getCode());
    }

    @Test
    @DisplayName("报名：活动未开始或已结束时返回业务异常")
    void signup_shouldRejectActivityNotOpen() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 2, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode(), ex.getCode());
        assertEquals("活动未开始或已结束", ex.getMessage());
        verify(activityMapper, never()).incrementParticipant(any(), any());
    }

    @Test
    @DisplayName("报名：重复报名被拦截")
    void signup_shouldRejectDuplicateEnrollment() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("您已报名该活动", ex.getMessage());
        verify(activityMapper, never()).incrementParticipant(any(), any());
    }

    @Test
    @DisplayName("报名：已在候补队列中的学生不可重复入队")
    void signup_shouldRejectAlreadyWaitingStudent() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(waitlist(77L, STUDENT_ID, ACTIVITY_ID, 1, 3));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("您已在候补队列中", ex.getMessage());
    }

    @Test
    @DisplayName("报名：历史取消记录（status=3）复用原行复活为已报名，不新增记录")
    void signup_shouldReuseCancelledRecord() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        StudentActivity cancelled = record(88L, STUDENT_ID, ACTIVITY_ID, 3);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(cancelled);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(ACTIVITY_ID, 0)).thenReturn(1);
        when(studentActivityMapper.updateById(any(StudentActivity.class))).thenReturn(1);

        ActivitySignupVO result = service.signup(signupRequest(ACTIVITY_ID));

        assertEquals("ENROLLED", result.getStatus());
        assertEquals(1, cancelled.getStatus());
        assertEquals(0, cancelled.getPointsAwarded());
        assertNotNull(cancelled.getSignupTime());
        verify(studentActivityMapper, never()).insert(any(StudentActivity.class));
        verify(studentActivityMapper).updateById(cancelled);
    }

    // ────────────────────────── 报名：候补入队 ──────────────────────────

    @Test
    @DisplayName("报名：名额已满且开启候补时入队，返回队尾序号与实时排位")
    void signup_shouldEnqueueWhenSeatsExhaustedAndWaitlistEnabled() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setWaitlistEnabled(1);
        Activity refreshed = activity(ACTIVITY_ID, 1, 1);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity, refreshed);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(any(), any())).thenReturn(0);
        when(waitlistMapper.selectList(any())).thenReturn(new ArrayList<>(
                List.of(waitlist(71L, 201L, ACTIVITY_ID, 1, 3), waitlist(72L, 202L, ACTIVITY_ID, 1, 7))));
        when(waitlistMapper.selectCount(any())).thenReturn(3L);
        when(waitlistMapper.insert(any(ActivityWaitlist.class))).thenAnswer(invocation -> {
            ActivityWaitlist entity = invocation.getArgument(0);
            entity.setId(730L);
            return 1;
        });

        ActivitySignupVO result = service.signup(signupRequest(ACTIVITY_ID));

        assertEquals("WAITLIST", result.getStatus());
        assertTrue(result.isWaitlist());
        assertEquals(3, result.getQueueNumber());
        assertTrue(result.getMessage().contains("第 3 位"), "实际提示：" + result.getMessage());

        ArgumentCaptor<ActivityWaitlist> captor = ArgumentCaptor.forClass(ActivityWaitlist.class);
        verify(waitlistMapper).insert(captor.capture());
        ActivityWaitlist saved = captor.getValue();
        assertEquals(8, saved.getQueueNumber(), "队尾序号应为当前最大有效号 + 1");
        assertEquals(1, saved.getStatus());
        assertEquals(0, saved.getDeleted());
        assertNull(saved.getPromotedTime());
        assertNotNull(saved.getWaitTime());
    }

    @Test
    @DisplayName("报名：名额已满且未开启候补时直接拒绝")
    void signup_shouldRejectWhenSeatsFullAndWaitlistDisabled() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setWaitlistEnabled(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity, activity(ACTIVITY_ID, 1, 1));
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("活动报名名额已满", ex.getMessage());
        verify(waitlistMapper, never()).insert(any(ActivityWaitlist.class));
    }

    @Test
    @DisplayName("报名：乐观锁连续 3 次冲突后抛出重试提示，且锁在异常路径下仍被释放")
    void signup_shouldFailAfterPersistentVersionConflicts() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(
                activity(ACTIVITY_ID, 1, 0),
                activity(ACTIVITY_ID, 1, 1),
                activity(ACTIVITY_ID, 1, 2),
                activity(ACTIVITY_ID, 1, 3));
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("报名人数更新冲突，请重试", ex.getMessage());
        verify(activityMapper, times(3)).incrementParticipant(any(), any());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("报名：重试期间活动被归档时立即按名额不足处理，不再空转重试")
    void signup_shouldStopRetryWhenActivityBecomesUnavailable() {
        Activity open = activity(ACTIVITY_ID, 1, 0);
        open.setWaitlistEnabled(0);
        Activity archived = activity(ACTIVITY_ID, 3, 1);
        archived.setWaitlistEnabled(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(open, archived);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.incrementParticipant(any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("活动报名名额已满", ex.getMessage());
        verify(activityMapper, times(1)).incrementParticipant(any(), any());
    }

    @Test
    @DisplayName("报名：分布式锁抢占失败时快速失败，不做任何写操作")
    void signup_shouldFailFastWhenLockIsBusy() throws Exception {
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertEquals("操作处理中，请勿重复提交", ex.getMessage());
        verify(activityMapper, never()).selectById(any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("报名：非学生身份应被鉴权拦截")
    void signup_shouldRequireStudentIdentity() {
        authUtil.when(AuthUtil::requireStudentId)
                .thenThrow(new BusinessException(401, "仅限学生端账号访问"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(401, ex.getCode());
    }

    // ────────────────────────── 取消报名与自动递补 ──────────────────────────

    @Test
    @DisplayName("取消报名：释放名额并把候补队首自动递补为正式报名")
    void cancel_shouldFreeSeatAndPromoteFirstWaiter() {
        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own, (StudentActivity) null);
        when(studentActivityMapper.updateById(any(StudentActivity.class))).thenReturn(1);
        when(studentActivityMapper.insert(any(StudentActivity.class))).thenAnswer(invocation -> {
            StudentActivity entity = invocation.getArgument(0);
            entity.setId(502L);
            return 1;
        });

        ActivityWaitlist first = waitlist(71L, 201L, ACTIVITY_ID, 1, 3);
        when(waitlistMapper.selectOne(any())).thenReturn(first);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(activityMapper.incrementParticipant(any(), any())).thenReturn(1);
        when(waitlistMapper.updateById(any(ActivityWaitlist.class))).thenReturn(1);

        String message = service.cancelSignup(ACTIVITY_ID);

        assertEquals("已取消报名", message);
        assertEquals(3, own.getStatus());
        verify(activityMapper).decrementParticipant(ACTIVITY_ID);

        ArgumentCaptor<StudentActivity> promoted = ArgumentCaptor.forClass(StudentActivity.class);
        verify(studentActivityMapper).insert(promoted.capture());
        assertEquals(201L, promoted.getValue().getStudentId());
        assertEquals(ACTIVITY_ID, promoted.getValue().getActivityId());
        assertEquals(1, promoted.getValue().getStatus());
        assertEquals(0, promoted.getValue().getPointsAwarded());

        assertEquals(2, first.getStatus());
        assertNotNull(first.getPromotedTime());
        verify(waitlistMapper).updateById(first);

        triggerAfterCommit();
        verify(activityCacheService).requestRefresh(ACTIVITY_ID);
        verify(sysNoticeService).createAndSendNotice(eq(2), eq(201L), eq("waitlist_promoted"),
                ArgumentMatchers.contains("候补转正"), ArgumentMatchers.contains("已成功递补转正"), eq(ACTIVITY_ID));
    }

    @Test
    @DisplayName("取消报名：队列为空时不触发递补，也不产生多余报名记录")
    void cancel_shouldNotPromoteWhenQueueIsEmpty() {
        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);
        when(studentActivityMapper.updateById(any(StudentActivity.class))).thenReturn(1);
        when(waitlistMapper.selectOne(any())).thenReturn(null);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));

        assertEquals("已取消报名", service.cancelSignup(ACTIVITY_ID));

        verify(activityMapper).decrementParticipant(ACTIVITY_ID);
        verify(studentActivityMapper, never()).insert(any(StudentActivity.class));
        verify(waitlistMapper, never()).updateById(any(ActivityWaitlist.class));
    }

    @Test
    @DisplayName("取消报名：候补学生本人取消时直接退队，不释放正式名额")
    void cancel_shouldRemoveStudentFromWaitlist() {
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        ActivityWaitlist waiting = waitlist(77L, STUDENT_ID, ACTIVITY_ID, 1, 5);
        when(waitlistMapper.selectOne(any())).thenReturn(waiting);
        when(waitlistMapper.updateById(any(ActivityWaitlist.class))).thenReturn(1);

        assertEquals("已退出候补队列", service.cancelSignup(ACTIVITY_ID));

        assertEquals(3, waiting.getStatus());
        verify(waitlistMapper).updateById(waiting);
        verify(activityMapper, never()).decrementParticipant(any());
    }

    @Test
    @DisplayName("取消报名：既无正式报名也无候补记录时返回 400")
    void cancel_shouldRejectWhenNoActiveRecord() {
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(waitlistMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancelSignup(ACTIVITY_ID));
        assertEquals(400, ex.getCode());
        assertEquals("未找到有效报名记录", ex.getMessage());
    }

    // ────────────────────────── 打卡：成功链路 ──────────────────────────

    @Test
    @DisplayName("打卡：校验通过后写回记录、积分入账、投递 MQ 事件并颁发电子证书")
    void checkin_shouldAwardPointsAndIssueCertificate() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setTitle("青年AI与新质生产力前沿讲座");
        activity.setPoints(15);
        activity.setCertificateEnabled(1);
        activity.setCertificateTitle("AI前沿学术讲座结业证书");
        activity.setCertificateIssuer("校科技创新协会");
        activity.setLocationCheckEnabled(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);
        when(studentActivityMapper.updateById(any(StudentActivity.class))).thenReturn(1);

        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));
        when(codeMapper.markUsed(400L, STUDENT_ID)).thenReturn(1);
        when(studentScoreService.rewardActivityCheckin(STUDENT_ID, 31L, "青年AI与新质生产力前沿讲座", 15))
                .thenReturn(195);
        when(certificateService.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate entity = invocation.getArgument(0);
            entity.setId(600L);
            return true;
        });

        ActivityCheckinVO result = service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null));

        assertEquals(15, result.getPointsAwarded());
        assertEquals(195, result.getCurrentPoints());
        assertTrue(result.isCertificateIssued());
        assertNotNull(result.getCheckinTime());

        assertEquals(2, own.getStatus());
        assertEquals(15, own.getPointsAwarded());
        assertEquals(NONCE, own.getQrNonceUsed());
        assertNotNull(own.getCheckinTime());
        verify(studentActivityMapper).updateById(own);

        ArgumentCaptor<Certificate> certCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateService).save(certCaptor.capture());
        Certificate certificate = certCaptor.getValue();
        assertTrue(certificate.getCertificateNo().startsWith("CERT-" + ACTIVITY_ID + "-" + STUDENT_ID + "-"));
        assertEquals("AI前沿学术讲座结业证书", certificate.getTitle());
        assertEquals("校科技创新协会", certificate.getIssuer());
        assertEquals(1, certificate.getStatus());
        assertEquals(0, certificate.getVerifyCount());
        assertEquals(0, certificate.getDeleted());
        assertEquals(STUDENT_ID, certificate.getStudentId());
        assertEquals(ACTIVITY_ID, certificate.getActivityId());
        assertNotNull(certificate.getIssueTime());

        // 打卡事件必须在事务提交后才投递
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        triggerAfterCommit();
        verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.ACTIVITY_EXCHANGE),
                eq(RabbitMqConfig.ACTIVITY_CHECKIN_ROUTING_KEY), any(Object.class));
    }

    @Test
    @DisplayName("打卡：活动未配置颁发证书时不生成证书记录")
    void checkin_shouldSkipCertificateWhenDisabled() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setPoints(10);
        activity.setCertificateEnabled(0);
        activity.setLocationCheckEnabled(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));
        when(codeMapper.markUsed(400L, STUDENT_ID)).thenReturn(1);
        when(studentScoreService.rewardActivityCheckin(any(), any(), any(), any())).thenReturn(150);

        ActivityCheckinVO result = service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null));

        assertFalse(result.isCertificateIssued());
        assertEquals(150, result.getCurrentPoints());
        verify(certificateService, never()).save(any(Certificate.class));
    }

    @Test
    @DisplayName("打卡：持有「学生与活动联合锁」，避免串行阻塞全场与其他活动并发竞争")
    void checkin_shouldAcquireBothActivityAndStudentLocks() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setPoints(10);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));
        when(codeMapper.markUsed(400L, STUDENT_ID)).thenReturn(1);
        when(studentScoreService.rewardActivityCheckin(any(), any(), any(), any())).thenReturn(110);

        service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null));

        verify(redissonClient).getLock("student:checkin:" + STUDENT_ID + ":" + ACTIVITY_ID);
        verify(lock, times(1)).unlock();
    }

    // ────────────────────────── 打卡：校验链失败分支 ──────────────────────────

    @Test
    @DisplayName("打卡：活动未处于进行中时返回业务异常")
    void checkin_shouldRejectActivityNotOngoing() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 0, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode(), ex.getCode());
        assertEquals("活动未开始或已结束", ex.getMessage());
        verify(codeMapper, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("打卡：未报名直接打卡被拒绝")
    void checkin_shouldRejectUnenrolledStudent() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals(400, ex.getCode());
        assertEquals("请先报名活动", ex.getMessage());
    }

    @Test
    @DisplayName("打卡：签到码不存在或已过期时拒绝")
    void checkin_shouldRejectMissingOrExpiredCode() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));

        when(codeMapper.selectOne(any())).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals(400, missing.getCode());
        assertEquals("签到码无效或已过期", missing.getMessage());

        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().minusSeconds(1)));
        BusinessException expired = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals("签到码无效或已过期", expired.getMessage());
    }

    @Test
    @DisplayName("打卡：学生已完成签到（status=2）时拒绝重复打卡，且广播码不会被标记消耗")
    void checkin_shouldRejectDuplicateCheckin() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setPoints(15);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 2);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals(400, ex.getCode());
        assertEquals("您已完成签到，请勿重复打卡", ex.getMessage());

        verify(codeMapper, never()).markUsed(anyLong(), anyLong());
        verify(studentScoreService, never()).rewardActivityCheckin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("打卡：开启定位核验但未上送坐标时拒绝")
    void checkin_shouldRejectMissingLocationWhenRequired() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(geoFencedActivity());
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN, null, null)));
        assertEquals(400, ex.getCode());
        assertEquals("请提供定位信息", ex.getMessage());
        verify(codeMapper, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("打卡：上送坐标非法（超出经纬度范围）时拒绝")
    void checkin_shouldRejectInvalidCoordinates() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(geoFencedActivity());
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN,
                        new BigDecimal("200"), new BigDecimal("114.362145"))));
        assertEquals(400, ex.getCode());
        assertEquals("定位信息无效", ex.getMessage());
    }

    @Test
    @DisplayName("打卡：GPS 围栏校验——超出签到半径拒绝且不核销签到码")
    void checkin_shouldRejectWhenOutOfRadius() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(geoFencedActivity());
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));

        // 纬度相差 0.1 度 ≈ 11.1 公里，远大于 300 米围栏
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN,
                        new BigDecimal("30.623412"), new BigDecimal("114.362145"))));
        assertEquals(400, ex.getCode());
        assertEquals("超出活动签到范围", ex.getMessage());
        verify(codeMapper, never()).markUsed(any(), any());
    }

    @Test
    @DisplayName("打卡：GPS 围栏校验——半径内打卡通过并记录实际签到距离")
    void checkin_shouldAcceptWithinRadiusAndRecordDistance() {
        Activity activity = geoFencedActivity();
        activity.setPoints(20);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));
        when(codeMapper.markUsed(400L, STUDENT_ID)).thenReturn(1);
        when(studentScoreService.rewardActivityCheckin(any(), any(), any(), any())).thenReturn(120);

        // 纬度 +0.0015 度 ≈ 167 米，落在 300 米围栏内
        ActivityCheckinVO result = service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN,
                new BigDecimal("30.524912"), new BigDecimal("114.362145")));

        assertEquals(20, result.getPointsAwarded());
        assertNotNull(own.getCheckinDistance());
        assertTrue(own.getCheckinDistance() > 0 && own.getCheckinDistance() <= 300,
                "实际签到距离应在围栏内，实测 " + own.getCheckinDistance());
    }

    @Test
    @DisplayName("打卡：活动签到半径配置非法（<=0）时拒绝")
    void checkin_shouldRejectInvalidRadiusConfiguration() {
        Activity activity = geoFencedActivity();
        activity.setLocationRadius(0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(studentActivityMapper.selectOne(any(), anyBoolean()))
                .thenReturn(record(31L, STUDENT_ID, ACTIVITY_ID, 1));
        when(codeMapper.selectOne(any())).thenReturn(checkinCode(400L, LocalDateTime.now().plusSeconds(60)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkin(checkinRequest(ACTIVITY_ID, NONCE, SIGN,
                        new BigDecimal("30.523412"), new BigDecimal("114.362145"))));
        assertEquals("活动签到范围配置无效", ex.getMessage());
    }

    // ────────────────────────── 我的活动记录 ──────────────────────────

    @Test
    @DisplayName("我的记录：按状态筛选时只查正式报名记录，不查候补表")
    void myList_shouldReturnOwnRecordsOnlyWhenStatusFiltered() {
        StudentActivity older = record(31L, STUDENT_ID, ACTIVITY_ID, 2);
        older.setSignupTime(LocalDateTime.now().minusDays(2));
        StudentActivity newer = record(32L, STUDENT_ID, 10L, 1);
        newer.setSignupTime(LocalDateTime.now().minusDays(1));
        when(studentActivityMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(older, newer)));
        when(activityMapper.selectById(any())).thenReturn(activity(10L, 1, 0));

        IPage<MyActivityVO> result = service.myList(1, 10, 1);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals(10L, result.getRecords().get(0).getActivityId(), "较晚报名的记录应排在前面");
        assertEquals(2, result.getRecords().get(1).getStatus());
        verify(waitlistMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("我的记录：status=4 只返回候补记录，并附带实时排位")
    void myList_shouldReturnWaitlistRecordsWhenStatusIsFour() {
        ActivityWaitlist waiting = waitlist(77L, STUDENT_ID, ACTIVITY_ID, 1, 6);
        waiting.setWaitTime(LocalDateTime.now().minusHours(3));
        when(waitlistMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(waiting)));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));
        when(waitlistMapper.selectCount(any())).thenReturn(2L);

        IPage<MyActivityVO> result = service.myList(1, 10, 4);

        assertEquals(1, result.getRecords().size());
        MyActivityVO vo = result.getRecords().get(0);
        assertEquals(4, vo.getStatus());
        assertEquals(2, vo.getQueueNumber());
        assertEquals(0, vo.getPointsAwarded());
        assertEquals(ACTIVITY_ID, vo.getActivityId());
        verify(studentActivityMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("我的记录：不传状态时合并正式与候补记录并按参与时间倒序")
    void myList_shouldMergeAndSortByTimeDescending() {
        StudentActivity own = record(31L, STUDENT_ID, 10L, 1);
        own.setSignupTime(LocalDateTime.now().minusHours(5));
        when(studentActivityMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(own)));

        ActivityWaitlist waiting = waitlist(77L, STUDENT_ID, ACTIVITY_ID, 1, 1);
        waiting.setWaitTime(LocalDateTime.now().minusHours(1));
        when(waitlistMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(waiting)));
        when(waitlistMapper.selectCount(any())).thenReturn(1L);
        when(activityMapper.selectById(any())).thenReturn(activity(ACTIVITY_ID, 1, 0));

        IPage<MyActivityVO> result = service.myList(1, 10, null);

        assertEquals(2, result.getTotal());
        assertEquals(4, result.getRecords().get(0).getStatus(), "较晚的候补记录应排在最前");
        assertEquals(1, result.getRecords().get(1).getStatus());
    }

    @Test
    @DisplayName("我的记录：候补所属活动已被删除时该条记录被过滤")
    void myList_shouldDropWaitlistOfDeletedActivity() {
        ActivityWaitlist waiting = waitlist(77L, STUDENT_ID, ACTIVITY_ID, 1, 1);
        waiting.setWaitTime(LocalDateTime.now());
        when(waitlistMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(waiting)));
        Activity deleted = activity(ACTIVITY_ID, 1, 0);
        deleted.setDeleted(1);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(deleted);

        IPage<MyActivityVO> result = service.myList(1, 10, 4);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("我的记录：内存分页边界钳制（页码下限 1、每页上限 100）且切片正确")
    void myList_shouldClampPagingAndSliceRecords() {
        List<StudentActivity> records = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            StudentActivity record = record((long) (31 + index), STUDENT_ID, ACTIVITY_ID, 1);
            record.setSignupTime(LocalDateTime.now().minusDays(index));
            records.add(record);
        }
        when(studentActivityMapper.selectList(any())).thenReturn(records);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ACTIVITY_ID, 1, 0));

        IPage<MyActivityVO> secondPage = service.myList(2, 2, null);
        assertEquals(3, secondPage.getTotal());
        assertEquals(1, secondPage.getRecords().size());
        assertEquals(2L, secondPage.getCurrent());
        assertEquals(2L, secondPage.getSize());

        IPage<MyActivityVO> clamped = service.myList(0, 500, null);
        assertEquals(1L, clamped.getCurrent());
        assertEquals(100L, clamped.getSize());
        assertEquals(3, clamped.getRecords().size());
    }

    // ────────────────────────── 管理端记录与指标 ──────────────────────────

    @Test
    @DisplayName("管理端记录列表：非管理员访问被拒绝")
    void adminList_shouldRejectNonAdmin() {
        authUtil.when(AuthUtil::requireAdminId)
                .thenThrow(new BusinessException(403, "无权访问管理端接口"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adminList(1, 10, null, null, null, null));
        assertEquals(403, ex.getCode());
        verify(studentActivityMapper, never()).selectAdminPage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("管理端记录列表：空白筛选条件归一化为 null，分页参数被钳制")
    void adminList_shouldTrimFiltersAndClampPaging() {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(ADMIN_ID);
        Page<ActivityRecordVO> mocked = new Page<>(1, 100);
        when(studentActivityMapper.selectAdminPage(any(), any(), any(), any(), any())).thenReturn(mocked);

        IPage<ActivityRecordVO> result = service.adminList(0, 500, "  张三  ", "   ", " 讲座 ", 2);

        assertEquals(mocked, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<ActivityRecordVO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(studentActivityMapper).selectAdminPage(pageCaptor.capture(), eq("张三"), isNull(), eq("讲座"), eq(2));
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(100L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("管理端打卡指标：SQL 聚合返回 null 时兜底为全零，避免前端 NaN")
    void getRecordMetrics_shouldFallbackToZeros() {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(ADMIN_ID);
        when(studentActivityMapper.selectRecordMetrics()).thenReturn(null);

        ActivityRecordMetricsVO result = service.getRecordMetrics();

        assertNotNull(result);
        assertEquals(0L, result.getTotalRecords());
        assertEquals(0L, result.getCompletedCount());
        assertEquals(0L, result.getPendingCount());
        assertEquals(0L, result.getCancelledCount());
        assertEquals(0L, result.getPointsDistributed());
    }

    @Test
    @DisplayName("管理端打卡指标：直接返回 SQL 下推聚合结果")
    void getRecordMetrics_shouldReturnAggregatedRow() {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(ADMIN_ID);
        ActivityRecordMetricsVO metrics = ActivityRecordMetricsVO.builder()
                .totalRecords(120L).completedCount(90L).pendingCount(20L)
                .cancelledCount(10L).pointsDistributed(1800L).build();
        when(studentActivityMapper.selectRecordMetrics()).thenReturn(metrics);

        ActivityRecordMetricsVO result = service.getRecordMetrics();

        assertEquals(120L, result.getTotalRecords());
        assertEquals(90L, result.getCompletedCount());
        assertEquals(1800L, result.getPointsDistributed());
    }

    @Test
    @DisplayName("P2-10 取消报名：活动物理时间已开始时拒绝取消")
    void cancel_shouldRejectWhenActivityAlreadyStarted() {
        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);

        Activity startedActivity = activity(ACTIVITY_ID, 0, 0);
        startedActivity.setStartTime(LocalDateTime.now().minusHours(1));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(startedActivity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelSignup(ACTIVITY_ID));
        assertEquals(400, ex.getCode());
        assertEquals("活动已开始或已结束，无法取消报名", ex.getMessage());
    }

    @Test
    @DisplayName("P2-10 取消报名：活动状态已为结束/归档时拒绝取消")
    void cancel_shouldRejectWhenActivityFinished() {
        StudentActivity own = record(31L, STUDENT_ID, ACTIVITY_ID, 1);
        when(studentActivityMapper.selectOne(any(), anyBoolean())).thenReturn(own);

        Activity finishedActivity = activity(ACTIVITY_ID, 2, 0);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(finishedActivity);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelSignup(ACTIVITY_ID));
        assertEquals(400, ex.getCode());
        assertEquals("活动已开始或已结束，无法取消报名", ex.getMessage());
    }

    @Test
    @DisplayName("P2-11 活动报名时间冲突防重叠：存在时间交叠时拒绝兼报")
    void signup_shouldRejectWhenTimeConflictDetected() {
        LocalDateTime now = LocalDateTime.now();
        Activity newActivity = activity(ACTIVITY_ID, 0, 0);
        newActivity.setStartTime(now.plusDays(1).withHour(14).withMinute(0));
        newActivity.setEndTime(now.plusDays(1).withHour(16).withMinute(0));
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(newActivity);

        Activity existingActivity = activity(88L, 0, 0);
        existingActivity.setTitle("人工智能研讨讲座");
        existingActivity.setStartTime(now.plusDays(1).withHour(15).withMinute(0)); // 15:00-17:00 交叠
        existingActivity.setEndTime(now.plusDays(1).withHour(17).withMinute(0));

        when(studentActivityMapper.selectActiveEnrolledActivities(eq(STUDENT_ID), eq(ACTIVITY_ID)))
                .thenReturn(List.of(existingActivity));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.signup(signupRequest(ACTIVITY_ID)));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("时间冲突，不可兼报"), "实际提示: " + ex.getMessage());
    }

    // ────────────────────────── 测试辅助 ──────────────────────────

    /** 手动触发已注册的事务同步回调，用于验证 afterCommit 语义。 */
    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

    private ActivitySignupDTO signupRequest(Long activityId) {
        ActivitySignupDTO dto = new ActivitySignupDTO();
        dto.setActivityId(activityId);
        return dto;
    }

    private ActivityCheckinDTO checkinRequest(Long activityId, String nonce, String sign,
                                              BigDecimal latitude, BigDecimal longitude) {
        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(activityId);
        dto.setNonce(nonce);
        dto.setSign(sign);
        dto.setCheckinLatitude(latitude);
        dto.setCheckinLongitude(longitude);
        return dto;
    }

    /** 开启 300 米定位核验、坐标位于武汉校区附近的活动。 */
    private Activity geoFencedActivity() {
        Activity activity = activity(ACTIVITY_ID, 1, 0);
        activity.setLocationCheckEnabled(1);
        activity.setLatitude(new BigDecimal("30.523412"));
        activity.setLongitude(new BigDecimal("114.362145"));
        activity.setLocationRadius(300);
        return activity;
    }

    private Activity activity(Long id, Integer status, Integer version) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle("校园活动 " + id);
        activity.setStatus(status);
        activity.setVersion(version);
        activity.setDeleted(0);
        activity.setParticipantCount(0);
        activity.setRegistrationLimit(100);
        activity.setPoints(10);
        activity.setWaitlistEnabled(1);
        activity.setLocationCheckEnabled(0);
        activity.setCertificateEnabled(0);
        return activity;
    }

    private StudentActivity record(Long id, Long studentId, Long activityId, Integer status) {
        StudentActivity record = new StudentActivity();
        record.setId(id);
        record.setStudentId(studentId);
        record.setActivityId(activityId);
        record.setStatus(status);
        record.setDeleted(0);
        return record;
    }

    private ActivityWaitlist waitlist(Long id, Long studentId, Long activityId, Integer status, Integer queueNumber) {
        ActivityWaitlist waitlist = new ActivityWaitlist();
        waitlist.setId(id);
        waitlist.setStudentId(studentId);
        waitlist.setActivityId(activityId);
        waitlist.setStatus(status);
        waitlist.setQueueNumber(queueNumber);
        waitlist.setDeleted(0);
        return waitlist;
    }

    private ActivityCheckinCode checkinCode(Long id, LocalDateTime expireTime) {
        ActivityCheckinCode code = new ActivityCheckinCode();
        code.setId(id);
        code.setActivityId(ACTIVITY_ID);
        code.setNonce(NONCE);
        code.setSign(SIGN);
        code.setStatus(0);
        code.setExpireTime(expireTime);
        return code;
    }
}
