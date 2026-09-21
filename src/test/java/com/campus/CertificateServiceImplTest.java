package com.campus;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.certificate.service.impl.CertificateServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口 8 /certificate 电子荣誉证书与验真模块 —— 服务层单元测试。
 *
 * 覆盖点：
 * 1. 学生端「我的证书」双重鉴权：必须登录、必须是学生身份、登录 ID 必须可解析
 * 2. 公开扫码验真：无需登录、姓名脱敏、撤销/过期证书禁止下载文件、验真次数自增
 * 3. 管理端台账分页：分页边界钳制与筛选条件空白归一化
 * 4. 撤销状态机：仅有效证书可撤销，撤销原因必填并去除首尾空格，同步清空文件地址
 */
@DisplayName("模块 8：电子荣誉证书与验真 (/certificate) 服务层测试")
class CertificateServiceImplTest {

    private static final Long STUDENT_ID = 55L;
    private static final Long ADMIN_ID = 7L;

    @Mock
    private CertificateMapper certificateMapper;

    private AutoCloseable mocks;
    private MockedStatic<StpUtil> stpUtil;
    private CertificateServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        stpUtil = mockStatic(StpUtil.class);

        service = new CertificateServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", certificateMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        stpUtil.close();
        mocks.close();
    }

    // ────────────────────────── 学生端：我的证书列表 ──────────────────────────

    @Test
    @DisplayName("我的证书：以 student: 身份登录时按学生 ID 查询并原样返回")
    void getMyList_shouldQueryByStudentLoginId() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("student:" + STUDENT_ID);
        List<CertificateMyVO> rows = List.of(new CertificateMyVO(
                1L, "CERT-2026-ACT2-A89F3B12", "青年AI前沿学术讲座",
                "AI前沿学术讲座结业证书", "校科技创新协会", LocalDateTime.now(), "https://oss/cert.png", 1));
        when(certificateMapper.selectMyList(STUDENT_ID)).thenReturn(rows);

        List<CertificateMyVO> result = service.getMyList();

        assertSame(rows, result);
        verify(certificateMapper).selectMyList(STUDENT_ID);
    }

    @Test
    @DisplayName("我的证书：未登录或登录标识为空时返回 403")
    void getMyList_shouldRejectBlankLoginId() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyList());
        assertEquals(403, ex.getCode());
        assertEquals("仅学生可查询个人证书", ex.getMessage());
        verify(certificateMapper, never()).selectMyList(any());
    }

    @Test
    @DisplayName("我的证书：管理员身份访问学生端接口被拒绝")
    void getMyList_shouldRejectAdminIdentity() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyList());
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("我的证书：登录标识无法解析为学生 ID 时返回 401")
    void getMyList_shouldRejectMalformedLoginId() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("student:abc");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyList());
        assertEquals(401, ex.getCode());
        assertEquals("登录状态无效", ex.getMessage());
    }

    // ────────────────────────── 公开验真 ──────────────────────────

    @Test
    @DisplayName("验真：证书编号为空或全空格时返回 400")
    void verify_shouldRejectBlankCertificateNo() {
        BusinessException blank = assertThrows(BusinessException.class, () -> service.verify(null));
        assertEquals(400, blank.getCode());
        assertEquals("证书编号不能为空", blank.getMessage());

        BusinessException spaces = assertThrows(BusinessException.class, () -> service.verify("   "));
        assertEquals(400, spaces.getCode());
        verify(certificateMapper, never()).selectVerifyByNo(any());
    }

    @Test
    @DisplayName("验真：证书编号不存在时返回 404")
    void verify_shouldRejectUnknownCertificate() {
        when(certificateMapper.selectVerifyByNo("CERT-NOT-EXIST")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verify("CERT-NOT-EXIST"));
        assertEquals(404, ex.getCode());
        assertEquals("证书不存在", ex.getMessage());
    }

    @Test
    @DisplayName("验真：编号自动 trim，有效证书返回姓名脱敏与可下载文件地址")
    void verify_shouldMaskNameAndKeepFileForValidCertificate() {
        CertificateVerifyVO row = verifyRow(1L, 1);
        row.setStudentName("张三");
        row.setFileUrl("https://oss/cert.png");
        when(certificateMapper.selectVerifyByNo("CERT-2026-ACT2-A89F3B12")).thenReturn(row);
        when(certificateMapper.incrementVerifyCount(1L)).thenReturn(1);

        CertificateVerifyVO result = service.verify("  CERT-2026-ACT2-A89F3B12  ");

        assertEquals(1, result.getStatus());
        assertEquals("有效", result.getStatusText());
        assertEquals("张*", result.getStudentName(), "公开验真只能暴露脱敏姓名");
        assertEquals("https://oss/cert.png", result.getFileUrl());
        verify(certificateMapper).incrementVerifyCount(1L);
    }

    @Test
    @DisplayName("验真：单字姓名与空姓名分别脱敏为 * 与 ***")
    void verify_shouldMaskSingleCharAndBlankName() {
        CertificateVerifyVO single = verifyRow(2L, 1);
        single.setStudentName("李");
        when(certificateMapper.selectVerifyByNo("CERT-SINGLE")).thenReturn(single);

        assertEquals("*", service.verify("CERT-SINGLE").getStudentName());

        CertificateVerifyVO blank = verifyRow(3L, 1);
        blank.setStudentName("   ");
        when(certificateMapper.selectVerifyByNo("CERT-BLANK")).thenReturn(blank);

        assertEquals("***", service.verify("CERT-BLANK").getStudentName());
    }

    @Test
    @DisplayName("验真：已撤销证书标记作废、保留撤销原因并禁止下载文件")
    void verify_shouldHideFileForRevokedCertificate() {
        CertificateVerifyVO row = verifyRow(4L, 2);
        row.setStudentName("王五");
        row.setRevokeReason("学生因违反竞赛作弊纪律，经学术委员会核定撤销证书");
        row.setFileUrl("https://oss/cert.png");
        when(certificateMapper.selectVerifyByNo("CERT-REVOKED")).thenReturn(row);

        CertificateVerifyVO result = service.verify("CERT-REVOKED");

        assertEquals(2, result.getStatus());
        assertEquals("已作废撤销", result.getStatusText());
        assertNull(result.getFileUrl(), "撤销证书不可再下载文件");
        assertEquals("学生因违反竞赛作弊纪律，经学术委员会核定撤销证书", result.getRevokeReason());
        assertEquals("王*", result.getStudentName());
    }

    @Test
    @DisplayName("验真：已过期证书标记过期并禁止下载文件")
    void verify_shouldHideFileForExpiredCertificate() {
        CertificateVerifyVO row = verifyRow(5L, 3);
        row.setStudentName("赵六");
        row.setFileUrl("https://oss/cert.png");
        when(certificateMapper.selectVerifyByNo("CERT-EXPIRED")).thenReturn(row);

        CertificateVerifyVO result = service.verify("CERT-EXPIRED");

        assertEquals(3, result.getStatus());
        assertEquals("已过期", result.getStatusText());
        assertNull(result.getFileUrl());
    }

    @Test
    @DisplayName("验真：非法状态值（null / 0 / 99）统一归一化为已过期并收紧文件下载")
    void verify_shouldNormalizeIllegalStatusToExpired() {
        for (Integer illegal : new Integer[]{null, 0, 99, -1}) {
            CertificateVerifyVO row = verifyRow(6L, illegal);
            row.setStudentName("孙七");
            row.setFileUrl("https://oss/cert.png");
            when(certificateMapper.selectVerifyByNo("CERT-ILLEGAL-" + illegal)).thenReturn(row);

            CertificateVerifyVO result = service.verify("CERT-ILLEGAL-" + illegal);

            assertEquals(3, result.getStatus(), "非法状态 " + illegal + " 应归一化为已过期");
            assertEquals("已过期", result.getStatusText());
            assertNull(result.getFileUrl());
        }
    }

    @Test
    @DisplayName("验真：证书主键缺失时不累计验真次数（避免空指针/脏更新）")
    void verify_shouldSkipVerifyCountWhenIdMissing() {
        CertificateVerifyVO row = verifyRow(null, 1);
        row.setStudentName("周八");
        when(certificateMapper.selectVerifyByNo("CERT-NO-ID")).thenReturn(row);

        CertificateVerifyVO result = service.verify("CERT-NO-ID");

        assertEquals("有效", result.getStatusText());
        verify(certificateMapper, never()).incrementVerifyCount(any());
    }

    // ────────────────────────── 管理端台账分页 ──────────────────────────

    @Test
    @DisplayName("台账分页：非管理员访问被拒绝")
    void getAdminPage_shouldRequireAdminIdentity() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("student:" + STUDENT_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getAdminPage(1, 10, null, null, null, null, null));
        assertEquals(403, ex.getCode());
        assertEquals("无权访问管理员证书接口", ex.getMessage());
        verify(certificateMapper, never()).selectAdminPage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("台账分页：登录标识非数字时返回 401")
    void getAdminPage_shouldRejectMalformedAdminLoginId() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:x");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getAdminPage(1, 10, null, null, null, null, null));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("台账分页：分页边界钳制、空白筛选条件归一化为 null")
    void getAdminPage_shouldClampPagingAndTrimFilters() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);
        Page<CertificateAdminVO> mocked = new Page<>(1, 100);
        when(certificateMapper.selectAdminPage(any(), any(), any(), any(), any(), any())).thenReturn(mocked);

        IPage<CertificateAdminVO> result = service.getAdminPage(0, 500,
                "  CERT-2026  ", "   ", "  ", " 讲座 ", 1);

        assertEquals(mocked, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<CertificateAdminVO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(certificateMapper).selectAdminPage(pageCaptor.capture(),
                eq("CERT-2026"), isNull(), isNull(), eq("讲座"), eq(1));
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(100L, pageCaptor.getValue().getSize());
    }

    @Test
    @DisplayName("台账分页：未传分页参数时使用默认值 1 / 10")
    void getAdminPage_shouldUseDefaultPagingWhenAbsent() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);
        Page<CertificateAdminVO> mocked = new Page<>(1, 10);
        when(certificateMapper.selectAdminPage(any(), any(), any(), any(), any(), any())).thenReturn(mocked);

        service.getAdminPage(null, null, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<CertificateAdminVO>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(certificateMapper).selectAdminPage(pageCaptor.capture(),
                isNull(), isNull(), isNull(), isNull(), isNull());
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
    }

    // ────────────────────────── 撤销证书 ──────────────────────────

    @Test
    @DisplayName("撤销证书：证书 ID 为空或撤销原因为空白时返回 400")
    void revoke_shouldRejectBlankIdOrReason() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);

        BusinessException nullId = assertThrows(BusinessException.class, () -> service.revoke(null, "作弊"));
        assertEquals(400, nullId.getCode());
        assertEquals("证书ID和撤销原因不能为空", nullId.getMessage());

        BusinessException blankReason = assertThrows(BusinessException.class, () -> service.revoke(1L, "   "));
        assertEquals(400, blankReason.getCode());

        BusinessException nullReason = assertThrows(BusinessException.class, () -> service.revoke(1L, null));
        assertEquals(400, nullReason.getCode());

        verify(certificateMapper, never()).updateById(any(Certificate.class));
    }

    @Test
    @DisplayName("撤销证书：证书不存在时返回 404")
    void revoke_shouldRejectMissingCertificate() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);
        when(certificateMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revoke(404L, "违规"));
        assertEquals(404, ex.getCode());
        assertEquals("证书不存在", ex.getMessage());
    }

    @Test
    @DisplayName("撤销证书：已撤销或已过期证书不可二次撤销")
    void revoke_shouldRejectNonEffectiveCertificate() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);

        Certificate revoked = certificate(4L, 2, "https://oss/cert.png");
        when(certificateMapper.selectById(4L)).thenReturn(revoked);
        BusinessException already = assertThrows(BusinessException.class,
                () -> service.revoke(4L, "重复撤销"));
        assertEquals(400, already.getCode());
        assertEquals("仅有效证书可以撤销", already.getMessage());

        Certificate expired = certificate(5L, 3, "https://oss/cert.png");
        when(certificateMapper.selectById(5L)).thenReturn(expired);
        BusinessException expiredEx = assertThrows(BusinessException.class,
                () -> service.revoke(5L, "过期撤销"));
        assertEquals("仅有效证书可以撤销", expiredEx.getMessage());

        verify(certificateMapper, never()).updateById(any(Certificate.class));
    }

    @Test
    @DisplayName("撤销证书：状态置 2、原因去空格、记录撤销管理员与时间并清空文件地址")
    void revoke_shouldMarkRevokedAndClearDownload() {
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin:" + ADMIN_ID);
        Certificate effective = certificate(1L, 1, "https://oss/cert.png");
        when(certificateMapper.selectById(1L)).thenReturn(effective);
        when(certificateMapper.updateById(any(Certificate.class))).thenReturn(1);

        service.revoke(1L, "  学生因违反竞赛作弊纪律，经学术委员会核定撤销证书  ");

        assertEquals(2, effective.getStatus());
        assertEquals("学生因违反竞赛作弊纪律，经学术委员会核定撤销证书", effective.getRevokeReason());
        assertEquals(ADMIN_ID, effective.getRevokedBy());
        assertNotNull(effective.getRevokedTime());
        assertNull(effective.getFileUrl(), "撤销后证书文件不可再下载");
        verify(certificateMapper).updateById(effective);
    }

    // ────────────────────────── 测试辅助 ──────────────────────────

    private CertificateVerifyVO verifyRow(Long id, Integer status) {
        CertificateVerifyVO vo = new CertificateVerifyVO();
        vo.setId(id);
        vo.setCertificateNo("CERT-2026-ACT2-A89F3B12");
        vo.setActivityTitle("“青年AI与新质生产力”前沿学术讲座");
        vo.setTitle("AI前沿学术讲座结业证书");
        vo.setIssuer("校科技创新协会");
        vo.setIssueTime(LocalDateTime.now());
        vo.setStatus(status);
        return vo;
    }

    private Certificate certificate(Long id, Integer status, String fileUrl) {
        Certificate certificate = new Certificate();
        certificate.setId(id);
        certificate.setCertificateNo("CERT-2026-ACT2-A89F3B12");
        certificate.setStudentId(STUDENT_ID);
        certificate.setActivityId(9L);
        certificate.setTitle("AI前沿学术讲座结业证书");
        certificate.setIssuer("校科技创新协会");
        certificate.setStatus(status);
        certificate.setFileUrl(fileUrl);
        certificate.setVerifyCount(0);
        certificate.setDeleted(0);
        return certificate;
    }
}
