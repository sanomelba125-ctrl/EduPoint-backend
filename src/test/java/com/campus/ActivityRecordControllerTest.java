package com.campus;

import cn.dev33.satoken.exception.NotLoginException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.modules.activity.controller.ActivityRecordController;
import com.campus.modules.activity.dto.ActivityRecordMetricsVO;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.service.StudentActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口 3.5 /activity/record 管理端全量参与记录与打卡指标 —— 控制器层测试。
 *
 * 覆盖点：多条件筛选参数绑定与默认分页、指标聚合端点、登录态异常统一翻译为 401。
 */
@DisplayName("模块 3.5：管理端参与记录与指标 (/activity/record) 控制器层测试")
class ActivityRecordControllerTest {

    @Mock
    private StudentActivityService service;

    private AutoCloseable mocks;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new ActivityRecordController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    @DisplayName("GET /activity/record/list：多条件筛选参数完整传递")
    void list_shouldBindAllFilters() throws Exception {
        Page<ActivityRecordVO> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(service.adminList(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/activity/record/list")
                        .param("page", "1")
                        .param("size", "20")
                        .param("studentName", "张三")
                        .param("studentNo", "2023001")
                        .param("activityTitle", "晨跑")
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).adminList(1, 20, "张三", "2023001", "晨跑", 2);
    }

    @Test
    @DisplayName("GET /activity/record/list：缺省筛选条件与分页默认值")
    void list_shouldUseDefaultsWhenParamsAbsent() throws Exception {
        Page<ActivityRecordVO> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(service.adminList(anyInt(), anyInt(), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/activity/record/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).adminList(1, 10, null, null, null, null);
    }

    @Test
    @DisplayName("GET /activity/record/list：非管理员访问透传 code=403")
    void list_shouldPropagateForbidden() throws Exception {
        when(service.adminList(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(403, "无权访问管理端接口"));

        mockMvc.perform(get("/activity/record/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问管理端接口"));
    }

    @Test
    @DisplayName("GET /activity/record/list：未登录（NotLoginException）统一翻译为 code=401")
    void list_shouldTranslateNotLoginException() throws Exception {
        when(service.adminList(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenThrow(new NotLoginException("账号未登录", "login", NotLoginException.NOT_TOKEN));

        mockMvc.perform(get("/activity/record/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号未登录或登录态已过期，请重新登录"));
    }

    @Test
    @DisplayName("GET /activity/record/metrics：返回打卡/报名聚合指标")
    void metrics_shouldReturnAggregatedCounters() throws Exception {
        when(service.getRecordMetrics()).thenReturn(ActivityRecordMetricsVO.builder()
                .totalRecords(120L).completedCount(90L).pendingCount(20L)
                .cancelledCount(10L).pointsDistributed(1800L).build());

        mockMvc.perform(get("/activity/record/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRecords").value(120))
                .andExpect(jsonPath("$.data.completedCount").value(90))
                .andExpect(jsonPath("$.data.pendingCount").value(20))
                .andExpect(jsonPath("$.data.cancelledCount").value(10))
                .andExpect(jsonPath("$.data.pointsDistributed").value(1800));
    }

    @Test
    @DisplayName("GET /activity/record/admin/metrics：兼容旧路径别名")
    void adminMetricsAlias_shouldWork() throws Exception {
        when(service.getRecordMetrics()).thenReturn(ActivityRecordMetricsVO.builder()
                .totalRecords(1L).completedCount(1L).pendingCount(0L)
                .cancelledCount(0L).pointsDistributed(15L).build());

        mockMvc.perform(get("/activity/record/admin/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalRecords").value(1));

        verify(service).getRecordMetrics();
    }
}
