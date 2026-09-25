package com.campus.modules.mall;

import com.campus.common.exception.BusinessException;
import com.campus.config.MallPickupProperties;
import com.campus.modules.mall.dto.GiftPickupQrCodeVO;
import com.campus.modules.mall.dto.PickupQrVerifyDTO;
import com.campus.modules.mall.dto.PickupVerifyResultVO;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.PickupTokenService;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.StudentGiftServiceImpl;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.service.SysNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("商城礼品专属动态提货二维码与扫码枪核销闭环 (P2-8) 测试")
class GiftPickupQrCodeTest {

    private static final Long ORDER_ID = 2001L;
    private static final String ORDER_NO = "GF202609230001";
    private static final Long STUDENT_ID = 66L;
    private static final Long ADMIN_ID = 8L;
    private static final String PICKUP_CODE = "PK-889911";

    private StudentGiftMapper studentGiftMapper;
    private GiftMapper giftMapper;
    private StudentMapper studentMapper;
    private PointLedger pointLedger;
    private SysNoticeService sysNoticeService;
    private RabbitTemplate rabbitTemplate;
    private StringRedisTemplate redis;
    private MallPickupProperties mallPickupProperties;
    private PickupTokenService pickupTokenService;
    private StudentGiftServiceImpl studentGiftService;

    @BeforeEach
    void setUp() {
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftMapper = mock(GiftMapper.class);
        studentMapper = mock(StudentMapper.class);
        pointLedger = mock(PointLedger.class);
        sysNoticeService = mock(SysNoticeService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        redis = mock(StringRedisTemplate.class);

        mallPickupProperties = new MallPickupProperties();
        mallPickupProperties.setValidSeconds(300);
        mallPickupProperties.setSignSecret("TestMallPickupSecret2026");

        pickupTokenService = new PickupTokenService(mallPickupProperties);

        studentGiftService = new StudentGiftServiceImpl(
                giftMapper,
                studentMapper,
                pointLedger,
                sysNoticeService,
                rabbitTemplate,
                redis,
                pickupTokenService,
                mallPickupProperties
        );
        ReflectionTestUtils.setField(studentGiftService, "baseMapper", studentGiftMapper);
    }

    // ==========================================
    // 1. PickupTokenService 防伪与过期校验
    // ==========================================

    @Test
    @DisplayName("Token服务：正常签发与验签成功解析")
    void tokenService_shouldGenerateAndVerifySuccessfully() {
        long now = System.currentTimeMillis();
        String token = pickupTokenService.generateToken(ORDER_NO, PICKUP_CODE, now);
        assertNotNull(token);

        PickupTokenService.PickupTokenPayload payload = pickupTokenService.verifyAndParseToken(token);
        assertEquals(ORDER_NO, payload.getOrderNo());
        assertEquals(PICKUP_CODE, payload.getPickupCode());
        assertEquals(now, payload.getTimestamp());
    }

    @Test
    @DisplayName("Token服务：超过有效期的动态码被判定过期拒绝")
    void tokenService_shouldRejectExpiredToken() {
        long pastTime = System.currentTimeMillis() - (mallPickupProperties.getValidSeconds() + 10) * 1000L;
        String token = pickupTokenService.generateToken(ORDER_NO, PICKUP_CODE, pastTime);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pickupTokenService.verifyAndParseToken(token));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("Token服务：篡改订单号或提货码导致签名不匹配拒绝")
    void tokenService_shouldRejectTamperedToken() {
        long now = System.currentTimeMillis();
        // 用错误密钥生成的签名
        MallPickupProperties fakeProp = new MallPickupProperties();
        fakeProp.setSignSecret("TamperedFakeSecretKey");
        PickupTokenService fakeService = new PickupTokenService(fakeProp);
        String forgedToken = fakeService.generateToken(ORDER_NO, PICKUP_CODE, now);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pickupTokenService.verifyAndParseToken(forgedToken));
        assertTrue(ex.getMessage().contains("防伪校验失败"));
    }

    // ==========================================
    // 2. 学生端获取动态提货二维码
    // ==========================================

    @Test
    @DisplayName("生成二维码：学生正常查询待提货订单生成包含 Base64 的二维码凭据")
    void getPickupQrCode_shouldReturnValidQrCodeVO() {
        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 1, PICKUP_CODE);
        when(studentGiftMapper.selectById(ORDER_ID)).thenReturn(order);

        GiftPickupQrCodeVO vo = studentGiftService.getPickupQrCode(ORDER_ID, STUDENT_ID);

        assertNotNull(vo);
        assertEquals(ORDER_ID, vo.getOrderId());
        assertEquals(ORDER_NO, vo.getOrderNo());
        assertEquals(PICKUP_CODE, vo.getPickupCode());
        assertNotNull(vo.getPickupToken());
        assertNotNull(vo.getQrCodeBase64());
        assertTrue(vo.getQrCodeBase64().startsWith("data:image/png;base64,"));
        assertEquals(300, vo.getValidSeconds());
        assertNotNull(vo.getExpireTime());
    }

    @Test
    @DisplayName("生成二维码：非本人订单禁止获取提货二维码")
    void getPickupQrCode_shouldRejectOtherStudent() {
        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 1, PICKUP_CODE);
        when(studentGiftMapper.selectById(ORDER_ID)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentGiftService.getPickupQrCode(ORDER_ID, 999L));
        assertEquals("无权查看他人的提货凭据", ex.getMessage());
    }

    @Test
    @DisplayName("生成二维码：已核销订单禁止再次生成提货二维码")
    void getPickupQrCode_shouldRejectAlreadyDeliveredOrder() {
        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 2, PICKUP_CODE);
        when(studentGiftMapper.selectById(ORDER_ID)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentGiftService.getPickupQrCode(ORDER_ID, STUDENT_ID));
        assertEquals("该订单已完成核销提货", ex.getMessage());
    }

    // ==========================================
    // 3. 扫码枪秒级核销
    // ==========================================

    @Test
    @DisplayName("扫码枪核销：合法动态码秒级核销成功，状态置为 2")
    void verifyAndDeliverByQrCode_shouldDeliverSuccessfully() {
        long now = System.currentTimeMillis();
        String token = pickupTokenService.generateToken(ORDER_NO, PICKUP_CODE, now);

        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 1, PICKUP_CODE);
        when(studentGiftMapper.selectOne(any())).thenReturn(order);
        when(studentGiftMapper.confirmDeliveryWithStatusGuard(eq(ORDER_ID), eq(1), any(LocalDateTime.class), eq(ADMIN_ID), anyString()))
                .thenReturn(1);

        PickupQrVerifyDTO dto = new PickupQrVerifyDTO();
        dto.setQrToken(token);
        dto.setRemark("东区一食堂服务台扫码枪");

        PickupVerifyResultVO result = studentGiftService.verifyAndDeliverByQrCode(dto, ADMIN_ID);

        assertNotNull(result);
        assertEquals(ORDER_ID, result.getOrderId());
        assertEquals(ORDER_NO, result.getOrderNo());
        assertEquals(2, result.getStatus());
        verify(studentGiftMapper).confirmDeliveryWithStatusGuard(eq(ORDER_ID), eq(1), any(LocalDateTime.class), eq(ADMIN_ID), anyString());
    }

    @Test
    @DisplayName("扫码枪核销：订单已处于已核销态时拒绝重复核销")
    void verifyAndDeliverByQrCode_shouldRejectAlreadyDelivered() {
        long now = System.currentTimeMillis();
        String token = pickupTokenService.generateToken(ORDER_NO, PICKUP_CODE, now);

        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 2, PICKUP_CODE);
        when(studentGiftMapper.selectOne(any())).thenReturn(order);

        PickupQrVerifyDTO dto = new PickupQrVerifyDTO();
        dto.setQrToken(token);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentGiftService.verifyAndDeliverByQrCode(dto, ADMIN_ID));
        assertTrue(ex.getMessage().contains("已完成核销"));
        verify(studentGiftMapper, never()).confirmDeliveryWithStatusGuard(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("扫码枪核销：并发竞争下状态守卫返回 0 影响行数时安全阻断")
    void verifyAndDeliverByQrCode_shouldRejectOnConcurrencyConflict() {
        long now = System.currentTimeMillis();
        String token = pickupTokenService.generateToken(ORDER_NO, PICKUP_CODE, now);

        StudentGift order = buildMockOrder(ORDER_ID, ORDER_NO, STUDENT_ID, 1, PICKUP_CODE);
        when(studentGiftMapper.selectOne(any())).thenReturn(order);
        when(studentGiftMapper.confirmDeliveryWithStatusGuard(eq(ORDER_ID), eq(1), any(LocalDateTime.class), eq(ADMIN_ID), anyString()))
                .thenReturn(0); // 并发被另一工作人员先核销

        PickupQrVerifyDTO dto = new PickupQrVerifyDTO();
        dto.setQrToken(token);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentGiftService.verifyAndDeliverByQrCode(dto, ADMIN_ID));
        assertEquals("该订单已被核销，请勿重复提货", ex.getMessage());
    }

    private StudentGift buildMockOrder(Long id, String orderNo, Long studentId, Integer status, String pickupCode) {
        StudentGift o = new StudentGift();
        o.setId(id);
        o.setOrderNo(orderNo);
        o.setStudentId(studentId);
        o.setStatus(status);
        o.setPickupCode(pickupCode);
        o.setGiftId(10L);
        o.setQuantity(1);
        o.setPointsCost(200);
        o.setDeleted(0);

        com.campus.modules.mall.entity.Gift gift = new com.campus.modules.mall.entity.Gift();
        gift.setId(10L);
        gift.setName("定制校园保温杯");
        when(giftMapper.selectById(10L)).thenReturn(gift);

        return o;
    }
}
