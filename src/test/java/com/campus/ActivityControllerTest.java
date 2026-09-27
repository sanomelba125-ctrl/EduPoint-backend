package com.campus;

import com.campus.common.exception.BusinessException;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.modules.activity.controller.ActivityController;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.dto.QrCodeVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.service.ActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口 2 /activity 校园活动管理与查询模块 —— 控制器层测试。
 *
 * 覆盖点：路由与 HTTP 动词映射、Query 参数绑定（含默认值）、请求体反序列化、
 * Bean Validation 校验失败响应、业务异常经全局异常处理器转成统一 Result 结构。
 */
@DisplayName("模块 2：校园活动管理与查询 (/activity) 控制器层测试")
class ActivityControllerTest {

    @Mock
    private ActivityService activityService;

    private AutoCloseable mocks;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        ActivityController controller = new ActivityController();
        ReflectionTestUtils.setField(controller, "activityService", activityService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ────────────────────────── 分页与详情 ──────────────────────────

    @Test
    @DisplayName("GET /activity/page：查询参数正确绑定到 ActivityPageQuery")
    void page_shouldBindQueryParameters() throws Exception {
        when(activityService.page(any(ActivityPageQuery.class))).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        mockMvc.perform(get("/activity/page")
                        .param("page", "2")
                        .param("size", "20")
                        .param("keyword", "晨跑")
                        .param("category", "SPORTS")
                        .param("status", "1")
                        .param("excludeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<ActivityPageQuery> captor = ArgumentCaptor.forClass(ActivityPageQuery.class);
        verify(activityService).page(captor.capture());
        ActivityPageQuery query = captor.getValue();
        assertEquals(2L, query.getPage());
        assertEquals(20L, query.getSize());
        assertEquals("晨跑", query.getKeyword());
        assertEquals("SPORTS", query.getCategory());
        assertEquals(1, query.getStatus());
        assertEquals(Boolean.TRUE, query.getExcludeArchived());
    }

    @Test
    @DisplayName("GET /activity/page：不传分页参数时使用 DTO 默认值 1 / 10")
    void page_shouldUseDefaultPaging() throws Exception {
        when(activityService.page(any(ActivityPageQuery.class))).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        mockMvc.perform(get("/activity/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ActivityPageQuery> captor = ArgumentCaptor.forClass(ActivityPageQuery.class);
        verify(activityService).page(captor.capture());
        assertEquals(1L, captor.getValue().getPage());
        assertEquals(10L, captor.getValue().getSize());
        assertEquals(null, captor.getValue().getExcludeArchived());
    }

    @Test
    @DisplayName("GET /activity/{id}：详情正常返回")
    void detail_shouldReturnActivity() throws Exception {
        Activity activity = new Activity();
        activity.setId(5L);
        activity.setTitle("校园晨跑");
        when(activityService.detail(5L)).thenReturn(activity);

        mockMvc.perform(get("/activity/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.title").value("校园晨跑"));
    }

    @Test
    @DisplayName("GET /activity/{id}：业务异常被翻译成 code=404 的统一响应体")
    void detail_shouldTranslateBusinessException() throws Exception {
        when(activityService.detail(404L))
                .thenThrow(new BusinessException(404, "活动不存在"));

        mockMvc.perform(get("/activity/{id}", 404L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("活动不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ────────────────────────── 创建 / 修改 / 归档 ──────────────────────────

    @Test
    @DisplayName("POST /activity/create：合法请求体返回创建成功与活动实体")
    void create_shouldReturnCreatedActivity() throws Exception {
        Activity created = new Activity();
        created.setId(101L);
        created.setTitle("新质生产力前沿讲座");
        when(activityService.create(any(ActivitySaveDTO.class))).thenReturn(created);

        String body = """
                {
                  "title": "新质生产力前沿讲座",
                  "points": 30,
                  "startTime": "2026-03-10T14:30:00",
                  "endTime": "2026-03-10T17:30:00",
                  "registrationLimit": 50,
                  "waitlistEnabled": 1,
                  "locationCheckEnabled": 1,
                  "longitude": 114.3642,
                  "latitude": 30.5241,
                  "locationRadius": 200,
                  "certificateEnabled": 1
                }
                """;

        mockMvc.perform(post("/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("活动创建成功"))
                .andExpect(jsonPath("$.data.id").value(101));

        ArgumentCaptor<ActivitySaveDTO> captor = ArgumentCaptor.forClass(ActivitySaveDTO.class);
        verify(activityService).create(captor.capture());
        ActivitySaveDTO dto = captor.getValue();
        assertEquals("新质生产力前沿讲座", dto.getTitle());
        assertEquals(30, dto.getPoints());
        assertEquals(50, dto.getRegistrationLimit());
        assertEquals(1, dto.getCertificateEnabled());
        assertEquals(0, dto.getLongitude().compareTo(new java.math.BigDecimal("114.3642")));
    }

    @Test
    @DisplayName("POST /activity/create：标题为空触发校验，返回 code=400 与校验提示")
    void create_shouldRejectBlankTitle() throws Exception {
        String body = """
                {
                  "title": "   ",
                  "points": 30,
                  "startTime": "2026-03-10T14:30:00",
                  "endTime": "2026-03-10T17:30:00",
                  "registrationLimit": 50
                }
                """;

        mockMvc.perform(post("/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("活动标题不能为空"));

        verify(activityService, never()).create(any(ActivitySaveDTO.class));
    }

    @Test
    @DisplayName("POST /activity/create：积分小于 1 触发 @Min 校验失败")
    void create_shouldRejectNonPositivePoints() throws Exception {
        String body = """
                {
                  "title": "零积分活动",
                  "points": 0,
                  "startTime": "2026-03-10T14:30:00",
                  "endTime": "2026-03-10T17:30:00",
                  "registrationLimit": 50
                }
                """;

        mockMvc.perform(post("/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("活动积分必须大于0"));
    }

    @Test
    @DisplayName("POST /activity/create：缺少必填时间字段触发校验失败")
    void create_shouldRejectMissingRequiredFields() throws Exception {
        String body = """
                {
                  "title": "缺字段活动",
                  "points": 10,
                  "registrationLimit": 50
                }
                """;

        mockMvc.perform(post("/activity/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("活动开始时间不能为空")));
    }

    @Test
    @DisplayName("PUT /activity/update：修改活动返回修改成功")
    void update_shouldReturnUpdatedActivity() throws Exception {
        Activity updated = new Activity();
        updated.setId(9L);
        updated.setTitle("已修改活动");
        when(activityService.update(any(ActivitySaveDTO.class))).thenReturn(updated);

        ActivitySaveDTO dto = new ActivitySaveDTO();
        dto.setId(9L);
        dto.setTitle("已修改活动");
        dto.setPoints(20);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(2));
        dto.setRegistrationLimit(100);

        mockMvc.perform(put("/activity/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("活动修改成功"));

        ArgumentCaptor<ActivitySaveDTO> captor = ArgumentCaptor.forClass(ActivitySaveDTO.class);
        verify(activityService).update(captor.capture());
        assertEquals(9L, captor.getValue().getId());
    }

    @Test
    @DisplayName("DELETE /activity/{id}：归档活动返回归档成功且 data 为空")
    void archive_shouldReturnArchivedMessage() throws Exception {
        mockMvc.perform(delete("/activity/{id}", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("活动已归档"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(activityService).archive(9L);
    }

    // ────────────────────────── 动态签到二维码 ──────────────────────────

    @Test
    @DisplayName("GET /activity/{id}/qrcode：返回 nonce、签名、有效期与扫码 URL")
    void getQrCode_shouldReturnSignedPayload() throws Exception {
        QrCodeVO vo = QrCodeVO.builder()
                .activityId(9L)
                .nonce("NONCE_7A9F8B2C4D1E00")
                .sign("SIGN_9A8B7C6D5E4F3A2B1C")
                .expireTime("2026-03-07 18:30:00")
                .validSeconds(300)
                .qrContent("http://localhost:5173/#/checkin?actId=9&nonce=NONCE_7A9F8B2C4D1E00&sign=SIGN_9A8B7C6D5E4F3A2B1C")
                .build();
        when(activityService.generateQrCode(9L)).thenReturn(vo);

        mockMvc.perform(get("/activity/{id}/qrcode", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("二维码生成成功"))
                .andExpect(jsonPath("$.data.activityId").value(9))
                .andExpect(jsonPath("$.data.nonce").value("NONCE_7A9F8B2C4D1E00"))
                .andExpect(jsonPath("$.data.validSeconds").value(300))
                .andExpect(jsonPath("$.data.qrContent").value(org.hamcrest.Matchers.containsString("actId=9")));
    }

    @Test
    @DisplayName("GET /activity/{id}/qrcode：活动已结束时透传业务码")
    void getQrCode_shouldPropagateExpiredActivityCode() throws Exception {
        when(activityService.generateQrCode(anyLong()))
                .thenThrow(new BusinessException(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode(), "已结束或已归档的活动不能生成签到码"));

        mockMvc.perform(get("/activity/{id}/qrcode", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.campus.common.result.ResultCode.ACTIVITY_EXPIRED.getCode()))
                .andExpect(jsonPath("$.message").value("已结束或已归档的活动不能生成签到码"));
    }

    @Test
    @DisplayName("HTTP 动词不匹配时返回 code=400 而不是容器错误页")
    void wrongHttpMethod_shouldReturnUnifiedErrorBody() throws Exception {
        mockMvc.perform(put("/activity/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(activityService, never()).page(any(ActivityPageQuery.class));
    }

    @Test
    @DisplayName("Result 响应体统一携带 code/message/timestamp 三个字段")
    void resultEnvelope_shouldCarryTimestamp() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        when(activityService.detail(1L)).thenReturn(activity);

        String json = mockMvc.perform(get("/activity/{id}", 1L))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(json.contains("\"timestamp\":null"), "响应体必须携带时间戳：" + json);
    }
}
