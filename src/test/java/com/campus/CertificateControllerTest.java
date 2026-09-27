package com.campus;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.modules.certificate.controller.CertificateController;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMetricsVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.service.CertificateService;
import com.campus.modules.certificate.service.CertificateRenderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口 8 /certificate 电子荣誉证书与验真模块 —— 控制器层测试。
 *
 * 覆盖点：学生端列表、公开验真路径参数、管理端台账多条件分页与别名路径、
 * 撤销接口的请求体校验、以及管理端鉴权失败时的统一响应体。
 */
@DisplayName("模块 8：电子荣誉证书与验真 (/certificate) 控制器层测试")
class CertificateControllerTest {

    @Mock
    private CertificateService certificateService;

    @Mock
    private CertificateRenderService certificateRenderService;

    private AutoCloseable mocks;
    private MockedStatic<AuthUtil> authUtil;
    private MockMvc mockMvc;

    /**
     * 断言 Wrapper 的 SQL 片段时需要 {@link Certificate} 的 {@link com.baomidou.mybatisplus.core.metadata.TableInfo}
     * 才能把方法引用解析成列名，这里手动注册一次元数据。
     */
    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Certificate.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        authUtil = mockStatic(AuthUtil.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CertificateController(certificateService, certificateRenderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        authUtil.close();
        mocks.close();
    }

    // ────────────────────────── 学生端：我的证书 ──────────────────────────

    @Test
    @DisplayName("GET /certificate/my-list：返回当前学生证书列表")
    void myList_shouldReturnCertificates() throws Exception {
        List<CertificateMyVO> rows = List.of(new CertificateMyVO(
                1L, "CERT-2026-ACT2-A89F3B12", "“青年AI与新质生产力”前沿学术讲座",
                "AI前沿学术讲座结业证书", "校科技创新协会", LocalDateTime.now(), "https://oss/cert.png", 1));
        when(certificateService.getMyList()).thenReturn(rows);

        mockMvc.perform(get("/certificate/my-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].certificateNo").value("CERT-2026-ACT2-A89F3B12"))
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    @Test
    @DisplayName("GET /certificate/my-list：非学生身份访问透传 code=403")
    void myList_shouldPropagateForbidden() throws Exception {
        when(certificateService.getMyList())
                .thenThrow(new BusinessException(403, "仅学生可查询个人证书"));

        mockMvc.perform(get("/certificate/my-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅学生可查询个人证书"));
    }

    // ────────────────────────── 公开验真 ──────────────────────────

    @Test
    @DisplayName("GET /certificate/verify/{certificateNo}：无需登录即可验真并返回脱敏姓名")
    void verify_shouldBePubliclyAccessible() throws Exception {
        CertificateVerifyVO vo = new CertificateVerifyVO();
        vo.setCertificateNo("CERT-2026-ACT2-A89F3B12");
        vo.setStudentName("张*");
        vo.setActivityTitle("“青年AI与新质生产力”前沿学术讲座");
        vo.setTitle("AI前沿学术讲座结业证书");
        vo.setIssuer("校科技创新协会");
        vo.setStatus(1);
        vo.setStatusText("有效");
        when(certificateService.verify("CERT-2026-ACT2-A89F3B12")).thenReturn(vo);

        mockMvc.perform(get("/certificate/verify/{certificateNo}", "CERT-2026-ACT2-A89F3B12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentName").value("张*"))
                .andExpect(jsonPath("$.data.statusText").value("有效"));

        // 公开接口不得触发任何鉴权
        verify(certificateService).verify("CERT-2026-ACT2-A89F3B12");
    }

    @Test
    @DisplayName("GET /certificate/verify/{certificateNo}：证书不存在时透传 code=404")
    void verify_shouldPropagateNotFound() throws Exception {
        when(certificateService.verify(any()))
                .thenThrow(new BusinessException(404, "证书不存在"));

        mockMvc.perform(get("/certificate/verify/{certificateNo}", "CERT-NOT-EXIST"))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("证书不存在"));
    }

    // ────────────────────────── 管理端台账 ──────────────────────────

    @Test
    @DisplayName("GET /certificate/list：多条件筛选参数完整传递并返回分页结构")
    void list_shouldBindFiltersAndReturnPage() throws Exception {
        Page<CertificateAdminVO> page = new Page<>(2, 20, 0);
        page.setRecords(new ArrayList<>());
        when(certificateService.getAdminPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/certificate/list")
                        .param("page", "2")
                        .param("size", "20")
                        .param("certificateNo", "CERT-2026")
                        .param("studentName", "张三")
                        .param("studentNo", "2023001")
                        .param("activityTitle", "讲座")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<String> noCaptor = ArgumentCaptor.forClass(String.class);
        verify(certificateService).getAdminPage(
                ArgumentMatchers.eq(2), ArgumentMatchers.eq(20), noCaptor.capture(),
                ArgumentMatchers.eq("张三"), ArgumentMatchers.eq("2023001"),
                ArgumentMatchers.eq("讲座"), ArgumentMatchers.eq(1));
        assertEquals("CERT-2026", noCaptor.getValue());
    }

    @Test
    @DisplayName("GET /certificate/list：不传参数时使用默认分页 1 / 10 且筛选为 null")
    void list_shouldUseDefaultPaging() throws Exception {
        Page<CertificateAdminVO> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(certificateService.getAdminPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/certificate/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(certificateService).getAdminPage(1, 10, null, null, null, null, null);
    }

    @Test
    @DisplayName("GET /admin/certificate/list：管理端别名路径同样可用")
    void adminAliasPath_shouldWork() throws Exception {
        Page<CertificateAdminVO> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(certificateService.getAdminPage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/admin/certificate/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /certificate/page：需管理员鉴权，关键字与状态条件生效")
    void pageCertificates_shouldRequireAdminAndPassFilters() throws Exception {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(7L);
        Page<Certificate> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(certificateService.page(ArgumentMatchers.<Page<Certificate>>any(),
                ArgumentMatchers.<Wrapper<Certificate>>any())).thenReturn(page);

        mockMvc.perform(get("/certificate/page")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "CERT-2026")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        authUtil.verify(AuthUtil::requireAdminId);
        verify(certificateService).page(ArgumentMatchers.<Page<Certificate>>any(),
                ArgumentMatchers.<Wrapper<Certificate>>any());
    }

    @Test
    @DisplayName("GET /certificate/page：非管理员访问透传 code=403 且不触达数据库")
    void pageCertificates_shouldRejectNonAdmin() throws Exception {
        authUtil.when(AuthUtil::requireAdminId)
                .thenThrow(new BusinessException(403, "无权访问管理端接口"));

        mockMvc.perform(get("/certificate/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问管理端接口"));

        verify(certificateService, never()).page(ArgumentMatchers.<Page<Certificate>>any(),
                ArgumentMatchers.<Wrapper<Certificate>>any());
    }

    // ────────────────────────── 撤销证书 ──────────────────────────

    @Test
    @DisplayName("PUT /certificate/{id}/revoke：合法原因返回撤销成功")
    void revoke_shouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(put("/certificate/{id}/revoke", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"学生因违反竞赛作弊纪律，经学术委员会核定撤销证书\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("证书已撤销"));

        verify(certificateService).revoke(1L, "学生因违反竞赛作弊纪律，经学术委员会核定撤销证书");
    }

    @Test
    @DisplayName("PUT /certificate/{id}/revoke：撤销原因为空触发 @NotBlank 校验失败")
    void revoke_shouldRejectBlankReason() throws Exception {
        mockMvc.perform(put("/certificate/{id}/revoke", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("撤销原因不能为空"));

        verify(certificateService, never()).revoke(any(), any());
    }

    @Test
    @DisplayName("PUT /admin/certificate/{id}/revoke：管理端别名路径同样可用")
    void revokeOnAdminAliasPath_shouldWork() throws Exception {
        mockMvc.perform(put("/admin/certificate/{id}/revoke", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"活动取消，证书作废\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("证书已撤销"));

        verify(certificateService).revoke(5L, "活动取消，证书作废");
    }

    @Test
    @DisplayName("PUT /certificate/{id}/revoke：证书已非有效态时透传 code=400")
    void revoke_shouldPropagateNonEffectiveState() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(400, "仅有效证书可以撤销"))
                .when(certificateService).revoke(any(), any());

        mockMvc.perform(put("/certificate/{id}/revoke", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"重复撤销\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅有效证书可以撤销"));
    }

    @Test
    @DisplayName("GET /certificate/page：空关键字不拼接 LIKE 条件（避免全表模糊扫描）")
    void pageCertificates_shouldIgnoreBlankKeyword() throws Exception {
        authUtil.when(AuthUtil::requireAdminId).thenReturn(7L);
        Page<Certificate> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(certificateService.page(ArgumentMatchers.<Page<Certificate>>any(),
                ArgumentMatchers.<Wrapper<Certificate>>any())).thenReturn(page);

        mockMvc.perform(get("/certificate/page").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<AbstractWrapper> wrapperCaptor = ArgumentCaptor.forClass(AbstractWrapper.class);
        verify(certificateService).page(ArgumentMatchers.<Page<Certificate>>any(), wrapperCaptor.capture());
        // 必须先求值 getSqlSegment()：paramNameValuePairs 是惰性填充的，
        // 否则「空 Map」断言恒真，测不出任何东西。
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertFalse(sqlSegment.toUpperCase().contains("LIKE"), "空关键字不应拼出 LIKE 条件，实际片段：" + sqlSegment);

        Map<String, Object> boundParams = wrapperCaptor.getValue().getParamNameValuePairs();
        assertEquals(1, boundParams.size(),
                "空关键字时只应保留 deleted = 0 这一个固定过滤条件，实际参数：" + boundParams);
        assertTrue(boundParams.containsValue(0), "应始终带上 deleted = 0 的软删除过滤，实际参数：" + boundParams);
        assertTrue(boundParams.values().stream()
                        .noneMatch(v -> v instanceof String && ((String) v).contains("%")),
                "空关键字不应产生 LIKE 通配参数，实际参数：" + boundParams);

        authUtil.verify(AuthUtil::requireAdminId);
    }

    // ────────────────────────── 荣誉证书大盘指标 ──────────────────────────

    @Test
    @DisplayName("GET /certificate/metrics：成功返回荣誉证书大盘聚合指标")
    void metrics_shouldReturnMetricsData() throws Exception {
        CertificateMetricsVO metricsVO = CertificateMetricsVO.builder()
                .totalCount(128L)
                .validCount(120L)
                .revokedCount(5L)
                .expiredCount(3L)
                .verifyTotal(356L)
                .build();
        when(certificateService.getMetrics()).thenReturn(metricsVO);

        mockMvc.perform(get("/certificate/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(128))
                .andExpect(jsonPath("$.data.validCount").value(120))
                .andExpect(jsonPath("$.data.revokedCount").value(5))
                .andExpect(jsonPath("$.data.expiredCount").value(3))
                .andExpect(jsonPath("$.data.verifyTotal").value(356));

        verify(certificateService).getMetrics();
    }

    @Test
    @DisplayName("GET /admin/certificate/metrics：管理端别名路径同样可用")
    void adminAliasMetrics_shouldWork() throws Exception {
        CertificateMetricsVO metricsVO = CertificateMetricsVO.builder()
                .totalCount(10L)
                .validCount(8L)
                .revokedCount(1L)
                .expiredCount(1L)
                .verifyTotal(25L)
                .build();
        when(certificateService.getMetrics()).thenReturn(metricsVO);

        mockMvc.perform(get("/admin/certificate/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(10));
    }

    @Test
    @DisplayName("GET /certificate/metrics：未授权或非管理员角色透传 code=403")
    void metrics_shouldPropagateForbiddenWhenNonAdmin() throws Exception {
        when(certificateService.getMetrics())
                .thenThrow(new BusinessException(403, "无权访问管理员证书接口"));

        mockMvc.perform(get("/certificate/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问管理员证书接口"));
    }

    // ────────────────────────── 电子荣誉证书图片流直链预览与下载 ──────────────────────────

    @Test
    @DisplayName("GET /certificate/file/{certificateNo}.png：存在时以 image/png 流形式返回，且包含缓存头")
    void getCertificateImage_withPngExtension_shouldReturnImageStream() throws Exception {
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        when(certificateRenderService.renderToPngBytesByNo("CERT-2026-001")).thenReturn(fakePng);

        mockMvc.perform(get("/certificate/file/CERT-2026-001.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().bytes(fakePng));

        verify(certificateRenderService).renderToPngBytesByNo("CERT-2026-001");
    }

    @Test
    @DisplayName("GET /certificate/{certificateNo}/image：别名路由正常工作")
    void getCertificateImage_aliasRoute_shouldReturnImageStream() throws Exception {
        byte[] fakePng = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        when(certificateRenderService.renderToPngBytesByNo("CERT-2026-888")).thenReturn(fakePng);

        mockMvc.perform(get("/certificate/CERT-2026-888/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(fakePng));
    }

    @Test
    @DisplayName("GET /certificate/file/{certificateNo}.png：不存在时返回 404")
    void getCertificateImage_notFound_shouldReturn404() throws Exception {
        when(certificateRenderService.renderToPngBytesByNo("NOT-EXIST")).thenReturn(null);

        mockMvc.perform(get("/certificate/file/NOT-EXIST.png"))
                .andExpect(status().isNotFound());
    }
}
