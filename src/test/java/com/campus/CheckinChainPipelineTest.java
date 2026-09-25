package com.campus;

import com.campus.common.exception.BusinessException;
import com.campus.config.ActivityQrCodeProperties;
import com.campus.modules.activity.chain.*;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityCheckinCodeMapper;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("责任链模式：活动签到核销流水线 (CheckinChainPipeline) 测试")
class CheckinChainPipelineTest {

    private static final Long ACTIVITY_ID = 10L;
    private static final Long STUDENT_ID = 88L;
    private static final String SIGN_SECRET = "test-secret-key-1234567890123456";

    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private StudentActivityMapper studentActivityMapper;
    @Mock
    private ActivityCheckinCodeMapper codeMapper;

    private ActivityQrCodeProperties qrCodeProperties;
    private ActivityStatusCheckinHandler statusHandler;
    private EnrollmentCheckinHandler enrollmentHandler;
    private DuplicateCheckinHandler duplicateHandler;
    private QrCodeHmacCheckinHandler hmacHandler;
    private GeoFenceCheckinHandler geoFenceHandler;
    private CheckinChainPipeline pipeline;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        qrCodeProperties = new ActivityQrCodeProperties();
        qrCodeProperties.setSignSecret(SIGN_SECRET);
        qrCodeProperties.setValidSeconds(300);

        statusHandler = new ActivityStatusCheckinHandler(activityMapper);
        enrollmentHandler = new EnrollmentCheckinHandler(studentActivityMapper);
        duplicateHandler = new DuplicateCheckinHandler();
        hmacHandler = new QrCodeHmacCheckinHandler(codeMapper);
        geoFenceHandler = new GeoFenceCheckinHandler();

        pipeline = new CheckinChainPipeline(List.of(
                geoFenceHandler,
                duplicateHandler,
                statusHandler,
                hmacHandler,
                enrollmentHandler
        ));
    }

    @Test
    @DisplayName("责任链：节点按 Order 升序自动排序 (10 -> 20 -> 30 -> 40 -> 50)")
    void pipeline_shouldSortHandlersByOrder() {
        List<CheckinHandler> handlers = pipeline.getHandlers();
        assertEquals(5, handlers.size());
        assertEquals(10, handlers.get(0).getOrder());
        assertEquals(20, handlers.get(1).getOrder());
        assertEquals(30, handlers.get(2).getOrder());
        assertEquals(40, handlers.get(3).getOrder());
        assertEquals(50, handlers.get(4).getOrder());

        assertInstanceOf(ActivityStatusCheckinHandler.class, handlers.get(0));
        assertInstanceOf(EnrollmentCheckinHandler.class, handlers.get(1));
        assertInstanceOf(DuplicateCheckinHandler.class, handlers.get(2));
        assertInstanceOf(QrCodeHmacCheckinHandler.class, handlers.get(3));
        assertInstanceOf(GeoFenceCheckinHandler.class, handlers.get(4));
    }

    @Test
    @DisplayName("完整责任链：合法参数、正常在场扫码打卡全流水线通过")
    void pipeline_shouldPassForValidCheckin() {
        String nonce = "NONCE_TEST123456";
        String rawSign = hmacSha256(ACTIVITY_ID + ":" + nonce, SIGN_SECRET);
        String sign = "SIGN_" + rawSign.toUpperCase();

        Activity activity = new Activity();
        activity.setId(ACTIVITY_ID);
        activity.setStatus(1);
        activity.setDeleted(0);
        activity.setLocationCheckEnabled(1);
        activity.setLatitude(java.math.BigDecimal.valueOf(31.2304));
        activity.setLongitude(java.math.BigDecimal.valueOf(121.4737));
        activity.setLocationRadius(500);

        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);

        StudentActivity studentActivity = new StudentActivity();
        studentActivity.setId(1001L);
        studentActivity.setStudentId(STUDENT_ID);
        studentActivity.setActivityId(ACTIVITY_ID);
        studentActivity.setStatus(1);
        when(studentActivityMapper.selectOne(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(studentActivity);

        ActivityCheckinCode code = new ActivityCheckinCode();
        code.setId(500L);
        code.setActivityId(ACTIVITY_ID);
        code.setNonce(nonce);
        code.setSign(sign);
        code.setStatus(0);
        code.setExpireTime(LocalDateTime.now().plusSeconds(180));
        when(codeMapper.selectOne(any())).thenReturn(code);

        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(ACTIVITY_ID);
        dto.setNonce(nonce);
        dto.setSign(sign);
        dto.setCheckinLatitude(java.math.BigDecimal.valueOf(31.2305));
        dto.setCheckinLongitude(java.math.BigDecimal.valueOf(121.4738));

        CheckinContext context = CheckinContext.builder()
                .studentId(STUDENT_ID)
                .dto(dto)
                .build();

        pipeline.execute(context);

        assertNotNull(context.getActivity());
        assertNotNull(context.getStudentActivity());
        assertNotNull(context.getCheckinCode());
        assertNotNull(context.getCheckinDistance());
        assertTrue(context.getCheckinDistance() < 100);
    }

    @Test
    @DisplayName("节点3 防重复打卡：学生记录 status=2 时直接阻断，抛出友好提示")
    void duplicateHandler_shouldRejectIfAlreadyCheckedIn() {
        StudentActivity alreadyCheckedIn = new StudentActivity();
        alreadyCheckedIn.setStatus(2);

        CheckinContext context = CheckinContext.builder()
                .studentId(STUDENT_ID)
                .studentActivity(alreadyCheckedIn)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> duplicateHandler.handle(context));
        assertEquals("您已完成签到，请勿重复打卡", ex.getMessage());
    }

    @Test
    @DisplayName("节点4 签到码核验：篡改签名或签到码不存在时数据库无匹配记录，被拒绝")
    void hmacHandler_shouldRejectTamperedSignature() {
        String nonce = "NONCE_TEST123456";
        String tamperedSign = "SIGN_TAMPERED_SIGNATURE_VALUE_XYZ";

        // 生产实现按 (activityId, nonce, sign, status=0) 过滤，篡改签名匹配不到记录
        when(codeMapper.selectOne(any())).thenReturn(null);

        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(ACTIVITY_ID);
        dto.setNonce(nonce);
        dto.setSign(tamperedSign);

        CheckinContext context = CheckinContext.builder()
                .studentId(STUDENT_ID)
                .dto(dto)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> hmacHandler.handle(context));
        assertEquals("签到码无效或已过期", ex.getMessage());
    }

    @Test
    @DisplayName("节点5 地理围栏：超出活动设定的 GPS 半径时阻断并报警")
    void geoFenceHandler_shouldRejectOutOfRange() {
        Activity activity = new Activity();
        activity.setLocationCheckEnabled(1);
        activity.setLatitude(java.math.BigDecimal.valueOf(31.2304));
        activity.setLongitude(java.math.BigDecimal.valueOf(121.4737));
        activity.setLocationRadius(100); // 100米

        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(ACTIVITY_ID);
        dto.setCheckinLatitude(java.math.BigDecimal.valueOf(31.2500)); // 距原点 > 2000米
        dto.setCheckinLongitude(java.math.BigDecimal.valueOf(121.4737));

        CheckinContext context = CheckinContext.builder()
                .studentId(STUDENT_ID)
                .activity(activity)
                .dto(dto)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> geoFenceHandler.handle(context));
        assertEquals("超出活动签到范围", ex.getMessage());
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
