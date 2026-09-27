package com.campus;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.config.ActivityQrCodeProperties;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.dto.QrCodeVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.ActivityWaitlistMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.activity.service.ActivityCheckinCodeService;
import com.campus.modules.activity.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口 2 /activity 校园活动管理与查询模块 —— 服务层单元测试。
 *
 * 覆盖点：
 * 1. 创建/修改活动的时间窗口校验与定位配置校验（范围、半径、坐标合法性）
 * 2. 活动状态由时间窗口自动推导（0 未开始 / 1 进行中 / 2 已结束），归档后不被覆盖
 * 3. 分页参数钳制（page >= 1、size <= 100）与关键字去空格
 * 4. 当前学生的正式报名 / 已完成 / 候补状态批量注入（消除 N+1）
 * 5. 动态签到二维码：旧码失效、nonce 唯一、HMAC-SHA256 签名、有效期与跳转 URL 拼装
 */
@DisplayName("模块 2：校园活动管理与查询 (/activity) 服务层测试")
class ActivityServiceImplTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long STUDENT_ID = 55L;
    private static final String SIGN_SECRET = "EduPoint@QrSign#2026";
    private static final String CHECKIN_BASE_URL = "http://localhost:5173/#/checkin";
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private ActivityCheckinCodeService checkinCodeService;
    @Mock
    private ActivityCacheService activityCacheService;
    @Mock
    private StudentActivityMapper studentActivityMapper;
    @Mock
    private ActivityWaitlistMapper activityWaitlistMapper;

    private AutoCloseable mocks;
    private MockedStatic<AuthUtil> authUtil;
    private ActivityServiceImpl service;

    /**
     * MyBatis-Plus 3.5.x 的 {@code IService#lambdaQuery()} 会通过 Mapper 代理反查实体类，
     * Mockito 生成的 Mock 不是 MyBatis 动态代理，故在测试子类中直接给出实体类型。
     * 同时预先注册 {@link TableInfo}，让 {@code Activity::getTitle} 之类的 Lambda 能解析成列名。
     */
    private static class TestActivityServiceImpl extends ActivityServiceImpl {
        @Override
        public Class<Activity> getEntityClass() {
            return Activity.class;
        }
    }

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        // GlobalConfig 的 dbConfig 默认不初始化，而 TableInfoHelper#initTableName 会直接取
        // dbConfig.getTablePrefix()，不显式注入会抛 NPE。
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Activity.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        authUtil = mockStatic(AuthUtil.class);

        ActivityQrCodeProperties qrCodeProperties = new ActivityQrCodeProperties();
        qrCodeProperties.setValidSeconds(300);
        qrCodeProperties.setSignSecret(SIGN_SECRET);
        qrCodeProperties.setCheckinBaseUrl(CHECKIN_BASE_URL);

        service = new TestActivityServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
        ReflectionTestUtils.setField(service, "checkinCodeService", checkinCodeService);
        ReflectionTestUtils.setField(service, "qrCodeProperties", qrCodeProperties);
        ReflectionTestUtils.setField(service, "activityCacheService", activityCacheService);
        ReflectionTestUtils.setField(service, "studentActivityMapper", studentActivityMapper);
        ReflectionTestUtils.setField(service, "activityWaitlistMapper", activityWaitlistMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        authUtil.close();
        mocks.close();
    }

    // ────────────────────────── 创建活动 ──────────────────────────

    @Test
    @DisplayName("创建活动：结束时间早于开始时间应被拒绝")
    void create_shouldRejectEndTimeBeforeStartTime() {
        ActivitySaveDTO dto = baseDto();
        dto.setStartTime(LocalDateTime.now().plusDays(2));
        dto.setEndTime(LocalDateTime.now().plusDays(1));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals("活动结束时间不能早于开始时间", ex.getMessage());
        verify(activityMapper, never()).insert(any(Activity.class));
    }

    @Test
    @DisplayName("创建活动：开启定位核验但未配置坐标应被拒绝")
    void create_shouldRejectLocationCheckWithoutCoordinates() {
        ActivitySaveDTO dto = baseDto();
        dto.setLocationCheckEnabled(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals("开启定位核验时必须配置活动坐标", ex.getMessage());
    }

    @Test
    @DisplayName("创建活动：定位核验半径小于 50 米应被拒绝")
    void create_shouldRejectRadiusBelowMinimum() {
        ActivitySaveDTO dto = baseDto();
        dto.setLocationCheckEnabled(1);
        dto.setLongitude(new BigDecimal("114.362145"));
        dto.setLatitude(new BigDecimal("30.523412"));
        dto.setLocationRadius(49);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals("定位核验半径不能小于50米", ex.getMessage());
    }

    @Test
    @DisplayName("创建活动：坐标超出经纬度有效范围应被拒绝")
    void create_shouldRejectOutOfRangeCoordinates() {
        ActivitySaveDTO dto = baseDto();
        dto.setLongitude(new BigDecimal("114.362145"));
        dto.setLatitude(new BigDecimal("100"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(dto));
        assertEquals(400, ex.getCode());
        assertEquals("活动坐标超出有效范围", ex.getMessage());
    }

    @Test
    @DisplayName("创建活动：开始时间在未来时状态推导为 0（未开始）")
    void create_shouldResolveStatusNotStarted() {
        ActivitySaveDTO dto = baseDto();
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));

        Activity saved = createAndCapture(dto);
        assertEquals(0, saved.getStatus());
    }

    @Test
    @DisplayName("创建活动：当前时间落在窗口内时状态推导为 1（进行中）")
    void create_shouldResolveStatusOngoing() {
        ActivitySaveDTO dto = baseDto();
        dto.setStartTime(LocalDateTime.now().minusHours(1));
        dto.setEndTime(LocalDateTime.now().plusHours(1));

        Activity saved = createAndCapture(dto);
        assertEquals(1, saved.getStatus());
    }

    @Test
    @DisplayName("创建活动：结束时间已过时状态推导为 2（已结束）")
    void create_shouldResolveStatusEnded() {
        ActivitySaveDTO dto = baseDto();
        dto.setStartTime(LocalDateTime.now().minusHours(3));
        dto.setEndTime(LocalDateTime.now().minusHours(1));

        Activity saved = createAndCapture(dto);
        assertEquals(2, saved.getStatus());
    }

    @Test
    @DisplayName("创建活动：默认值兜底、标题去空格、初始报名人数归零并失效缓存")
    void create_shouldPersistDefaultsAndEvictCache() {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(ADMIN_ID);
        when(activityMapper.insert(any(Activity.class))).thenAnswer(invocation -> {
            Activity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });

        ActivitySaveDTO dto = baseDto();
        dto.setCategory("   ");
        dto.setWaitlistEnabled(null);
        dto.setLocationRadius(null);
        dto.setLocationCheckEnabled(null);
        dto.setCertificateEnabled(null);

        Activity result = service.create(dto);

        assertEquals(101L, result.getId());
        assertEquals("校园晨跑", result.getTitle());
        assertEquals("OTHER", result.getCategory());
        assertEquals(1, result.getWaitlistEnabled());
        assertEquals(300, result.getLocationRadius());
        assertEquals(0, result.getLocationCheckEnabled());
        assertEquals(0, result.getCertificateEnabled());
        assertEquals(0, result.getParticipantCount());
        assertEquals(0, result.getDeleted());
        assertEquals(ADMIN_ID, result.getCreatedBy());
        assertEquals(ADMIN_ID, result.getUpdatedBy());
        verify(activityCacheService).evict(101L);
    }

    @Test
    @DisplayName("创建活动：必须为管理员身份（非管理员直接抛 403）")
    void create_shouldRequireAdminIdentity() {
        authUtil.when(AuthUtil::requireAdminId)
                .thenThrow(new BusinessException(403, "无权访问管理端接口"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(baseDto()));
        assertEquals(403, ex.getCode());
        verify(activityMapper, never()).insert(any(Activity.class));
    }

    // ────────────────────────── 修改 / 归档 ──────────────────────────

    @Test
    @DisplayName("修改活动：缺少活动 ID 应被拒绝")
    void update_shouldRejectMissingId() {
        ActivitySaveDTO dto = baseDto();
        dto.setId(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));
        assertEquals(400, ex.getCode());
        assertEquals("活动ID不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("修改活动：活动不存在应返回 404")
    void update_shouldRejectMissingActivity() {
        ActivitySaveDTO dto = baseDto();
        dto.setId(999L);
        when(activityMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));
        assertEquals(404, ex.getCode());
        assertEquals("活动不存在", ex.getMessage());
        verify(activityMapper, never()).updateById(any(Activity.class));
    }

    @Test
    @DisplayName("修改活动：逻辑已删除的活动视为不存在")
    void update_shouldRejectLogicallyDeletedActivity() {
        ActivitySaveDTO dto = baseDto();
        dto.setId(9L);
        Activity deleted = activity(9L, 1, 0);
        deleted.setDeleted(1);
        when(activityMapper.selectById(9L)).thenReturn(deleted);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("修改活动：已归档（status=3）的活动保持归档态，不被时间窗口重新推导")
    void update_shouldKeepArchivedStatus() {
        ActivitySaveDTO dto = baseDto();
        dto.setId(9L);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));

        Activity archived = activity(9L, 3, 0);
        when(activityMapper.selectById(9L)).thenReturn(archived);
        when(activityMapper.updateById(any(Activity.class))).thenReturn(1);

        Activity result = service.update(dto);

        assertEquals(3, result.getStatus());
        verify(activityCacheService).evict(9L);
    }

    @Test
    @DisplayName("修改活动：未归档活动的状态按新时间窗口重算")
    void update_shouldRecomputeStatusForActiveActivity() {
        ActivitySaveDTO dto = baseDto();
        dto.setId(9L);
        dto.setStartTime(LocalDateTime.now().minusHours(1));
        dto.setEndTime(LocalDateTime.now().plusHours(1));

        when(activityMapper.selectById(9L)).thenReturn(activity(9L, 0, 0));
        when(activityMapper.updateById(any(Activity.class))).thenReturn(1);

        Activity result = service.update(dto);

        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("归档活动：状态置为 3、记录操作管理员并失效缓存")
    void archive_shouldSetClosedStatusAndEvictCache() {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(ADMIN_ID);
        Activity activity = activity(9L, 1, 0);
        when(activityMapper.selectById(9L)).thenReturn(activity);
        when(activityMapper.updateById(any(Activity.class))).thenReturn(1);

        service.archive(9L);

        assertEquals(3, activity.getStatus());
        assertEquals(ADMIN_ID, activity.getUpdatedBy());
        verify(activityMapper).updateById(activity);
        verify(activityCacheService).evict(9L);
    }

    @Test
    @DisplayName("归档活动：活动不存在应返回 404")
    void archive_shouldRejectMissingActivity() {
        when(activityMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.archive(404L));
        assertEquals(404, ex.getCode());
        verify(activityMapper, never()).updateById(any(Activity.class));
    }

    // ────────────────────────── 详情 / 分页 ──────────────────────────

    @Test
    @DisplayName("查询详情：未登录访客的参与状态统一为 NONE，候补序号为空")
    void detail_shouldMarkNoneForAnonymousVisitor() {
        Activity cached = activity(9L, 1, 0);
        when(activityCacheService.getDetail(9L)).thenReturn(cached);
        authUtil.when(AuthUtil::isStudent).thenReturn(false);

        Activity result = service.detail(9L);

        assertSame(cached, result);
        assertEquals("NONE", result.getMyEnrollmentStatus());
        assertNull(result.getMyQueueNumber());
        verify(studentActivityMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("查询详情：缓存未命中或已删除应返回 404")
    void detail_shouldRejectMissingActivity() {
        when(activityCacheService.getDetail(9L)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class, () -> service.detail(9L));
        assertEquals(404, missing.getCode());

        Activity deleted = activity(8L, 1, 0);
        deleted.setDeleted(1);
        when(activityCacheService.getDetail(8L)).thenReturn(deleted);
        BusinessException removed = assertThrows(BusinessException.class, () -> service.detail(8L));
        assertEquals(404, removed.getCode());
    }

    @Test
    @DisplayName("分页查询：页码下限钳制为 1、每页上限钳制为 100，关键字去除首尾空格")
    void page_shouldClampPagingAndTrimKeyword() {
        Page<Activity> mocked = new Page<>(1, 100, 0);
        mocked.setRecords(new ArrayList<>());
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<Activity>>any(),
                ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(mocked);

        ActivityPageQuery query = new ActivityPageQuery();
        query.setPage(0);
        query.setSize(999);
        query.setKeyword("  晨跑  ");

        IPage<Activity> result = service.page(query);

        assertSame(mocked, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<Activity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(activityMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());

        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(100L, pageCaptor.getValue().getSize());
        // MyBatis-Plus 的 paramNameValuePairs 是惰性填充的，必须先触发 getSqlSegment()
        // 让占位符真正生成，否则读到的永远是空 Map。
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.toUpperCase().contains("LIKE"), "关键字应参与 LIKE 匹配，实际片段：" + sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue("%晨跑%"),
                "关键字应被 trim 后用于 LIKE 匹配，实际参数：" + wrapperCaptor.getValue().getParamNameValuePairs());
    }

    @Test
    @DisplayName("分页查询：excludeArchived 开启时拼装 status <> 3 的过滤条件")
    void page_shouldExcludeArchivedWhenFlagEnabled() {
        Page<Activity> mocked = new Page<>(1, 10, 0);
        mocked.setRecords(new ArrayList<>());
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<Activity>>any(),
                ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(mocked);

        ActivityPageQuery query = new ActivityPageQuery();
        query.setExcludeArchived(true);

        service.page(query);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(activityMapper).selectPage(ArgumentMatchers.<IPage<Activity>>any(), wrapperCaptor.capture());

        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("<>"), "应生成不等于条件，实际片段：" + sqlSegment);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(3));
    }

    @Test
    @DisplayName("分页查询：学生已打卡完成时注入 COMPLETED 状态")
    void page_shouldInjectCompletedStatusForStudent() {
        Activity activity = activity(11L, 1, 0);
        Page<Activity> mocked = new Page<>(1, 10, 1);
        mocked.setRecords(new ArrayList<>(List.of(activity)));
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<Activity>>any(),
                ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(mocked);

        authUtil.when(AuthUtil::isStudent).thenReturn(true);
        authUtil.when(AuthUtil::requireStudentId).thenReturn(STUDENT_ID);
        when(studentActivityMapper.selectList(any())).thenReturn(List.of(record(31L, STUDENT_ID, 11L, 2)));

        service.page(new ActivityPageQuery());

        assertEquals("COMPLETED", activity.getMyEnrollmentStatus());
        verify(activityWaitlistMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("分页查询：候补中的学生注入 WAITLIST 状态并附带实时排位")
    void page_shouldInjectWaitlistStatusAndLiveRank() {
        Activity activity = activity(11L, 1, 0);
        Page<Activity> mocked = new Page<>(1, 10, 1);
        mocked.setRecords(new ArrayList<>(List.of(activity)));
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<Activity>>any(),
                ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(mocked);

        authUtil.when(AuthUtil::isStudent).thenReturn(true);
        authUtil.when(AuthUtil::requireStudentId).thenReturn(STUDENT_ID);
        when(studentActivityMapper.selectList(any())).thenReturn(new ArrayList<>());

        ActivityWaitlist waitlist = new ActivityWaitlist();
        waitlist.setId(77L);
        waitlist.setActivityId(11L);
        waitlist.setStudentId(STUDENT_ID);
        waitlist.setQueueNumber(9);
        waitlist.setStatus(1);
        when(activityWaitlistMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(waitlist)));
        when(activityWaitlistMapper.selectCount(any())).thenReturn(4L);

        service.page(new ActivityPageQuery());

        assertEquals("WAITLIST", activity.getMyEnrollmentStatus());
        assertEquals(4, activity.getMyQueueNumber());
    }

    @Test
    @DisplayName("分页查询：空结果集不应触发学生状态查询")
    void page_shouldSkipStatusInjectionWhenNoRecords() {
        Page<Activity> mocked = new Page<>(1, 10, 0);
        mocked.setRecords(new ArrayList<>());
        when(activityMapper.selectPage(ArgumentMatchers.<IPage<Activity>>any(),
                ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(mocked);

        service.page(new ActivityPageQuery());

        verify(studentActivityMapper, never()).selectList(any());
        verify(activityWaitlistMapper, never()).selectList(any());
    }

    // ────────────────────────── 动态签到二维码 ──────────────────────────

    @Test
    @DisplayName("生成二维码：活动不存在应返回 404")
    void generateQrCode_shouldRejectMissingActivity() {
        when(activityMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateQrCode(404L));
        assertEquals(404, ex.getCode());
        verify(checkinCodeService, never()).save(any(ActivityCheckinCode.class));
    }

    @Test
    @DisplayName("生成二维码：已结束/已归档活动应返回业务异常")
    void generateQrCode_shouldRejectEndedOrArchivedActivity() {
        when(activityMapper.selectById(9L)).thenReturn(activity(9L, 2, 0));
        BusinessException ended = assertThrows(BusinessException.class, () -> service.generateQrCode(9L));
        assertEquals(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode(), ended.getCode());
        assertEquals("已结束或已归档的活动不能生成签到码", ended.getMessage());

        when(activityMapper.selectById(10L)).thenReturn(activity(10L, 3, 0));
        BusinessException archived = assertThrows(BusinessException.class, () -> service.generateQrCode(10L));
        assertEquals(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode(), archived.getCode());
    }

    @Test
    @DisplayName("生成二维码：先失效旧码，再落库带 HMAC-SHA256 签名的新码并拼装扫码 URL")
    void generateQrCode_shouldInvalidateOldCodesAndIssueSignedToken() throws Exception {
        when(activityMapper.selectById(9L)).thenReturn(activity(9L, 1, 0));

        QrCodeVO vo = service.generateQrCode(9L);

        // 1. 旧的待核销码必须全部失效，防止旧二维码继续可用
        verify(checkinCodeService).invalidateActiveCodes(9L);

        // 2. 响应字段结构
        assertEquals(9L, vo.getActivityId());
        assertEquals(300, vo.getValidSeconds());
        assertNotNull(vo.getNonce());
        assertTrue(vo.getNonce().startsWith("NONCE_"), "nonce 前缀应为 NONCE_");
        assertEquals("NONCE_".length() + 14, vo.getNonce().length(), "nonce 应为 14 位去横线 UUID");
        assertTrue(vo.getSign().startsWith("SIGN_"), "签名字段前缀应为 SIGN_");
        assertNotNull(vo.getQrCodeBase64(), "应包含二维码 Base64 Data URL 图片");
        assertTrue(vo.getQrCodeBase64().startsWith("data:image/png;base64,"), "Base64 应为标准 PNG Data URL");

        // 3. 签名算法可被独立复算：sign = Base64Url(HMAC-SHA256(activityId + ":" + nonce, secret)) 全量防篡改签名
        String expected = hmacSha256(9L + ":" + vo.getNonce(), SIGN_SECRET);
        assertEquals("SIGN_" + expected.toUpperCase(), vo.getSign());

        // 4. 有效期与过期时间
        LocalDateTime expireTime = LocalDateTime.parse(vo.getExpireTime(), DATETIME_FMT);
        long seconds = java.time.Duration.between(LocalDateTime.now(), expireTime).getSeconds();
        assertTrue(seconds > 290 && seconds <= 301, "有效期应约等于 300 秒，实际 " + seconds);

        // 5. 落库实体与响应一致
        ArgumentCaptor<ActivityCheckinCode> captor = ArgumentCaptor.forClass(ActivityCheckinCode.class);
        verify(checkinCodeService).save(captor.capture());
        ActivityCheckinCode saved = captor.getValue();
        assertEquals(9L, saved.getActivityId());
        assertEquals(vo.getNonce(), saved.getNonce());
        assertEquals(vo.getSign(), saved.getSign());
        assertEquals(0, saved.getStatus());
        assertEquals(expireTime, saved.getExpireTime().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

        // 6. 扫码跳转 URL 携带三个必要参数
        assertEquals(CHECKIN_BASE_URL + "?actId=9&nonce=" + vo.getNonce() + "&sign=" + vo.getSign(),
                vo.getQrContent());
    }

    @Test
    @DisplayName("生成二维码：同活动连续刷新应产生互不相同的 nonce（防重放）")
    void generateQrCode_shouldProduceUniqueNoncePerRefresh() {
        when(activityMapper.selectById(9L)).thenReturn(activity(9L, 1, 0));

        QrCodeVO first = service.generateQrCode(9L);
        QrCodeVO second = service.generateQrCode(9L);

        assertTrue(!first.getNonce().equals(second.getNonce()), "两次刷新 nonce 必须不同");
        assertTrue(!first.getSign().equals(second.getSign()), "nonce 不同则签名必须不同");
        verify(checkinCodeService, times(2)).invalidateActiveCodes(9L);
    }

    // ────────────────────────── 测试辅助 ──────────────────────────

    private Activity createAndCapture(ActivitySaveDTO dto) {
        when(activityMapper.insert(any(Activity.class))).thenAnswer(invocation -> {
            Activity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });
        service.create(dto);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityMapper).insert(captor.capture());
        return captor.getValue();
    }

    private ActivitySaveDTO baseDto() {
        ActivitySaveDTO dto = new ActivitySaveDTO();
        dto.setTitle("  校园晨跑  ");
        dto.setPoints(15);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));
        dto.setRegistrationLimit(100);
        return dto;
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
        return activity;
    }

    private StudentActivity record(Long id, Long studentId, Long activityId, Integer status) {
        StudentActivity record = new StudentActivity();
        record.setId(id);
        record.setStudentId(studentId);
        record.setActivityId(activityId);
        record.setStatus(status);
        return record;
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
