package com.campus;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.modules.activity.controller.StudentActivityController;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.dto.ActivityCheckinVO;
import com.campus.modules.activity.dto.ActivitySignupDTO;
import com.campus.modules.activity.dto.ActivitySignupVO;
import com.campus.modules.activity.dto.MyActivityVO;
import com.campus.modules.activity.service.StudentActivityService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口 3 /student/activity 活动报名、候补与动态打卡模块 —— 控制器层测试。
 *
 * 覆盖点：报名/取消/打卡/我的记录四个端点的路由与响应包装、请求体校验、
 * 成功提示语与业务异常码的透传。
 */
@DisplayName("模块 3：活动报名/候补/打卡 (/student/activity) 控制器层测试")
class StudentActivityControllerTest {

    @Mock
    private StudentActivityService service;

    private AutoCloseable mocks;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentActivityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ────────────────────────── 报名 ──────────────────────────

    @Test
    @DisplayName("POST /student/activity/signup：名额未满时返回正式报名结果")
    void signup_shouldReturnEnrolledResult() throws Exception {
        when(service.signup(any(ActivitySignupDTO.class))).thenReturn(ActivitySignupVO.builder()
                .status("ENROLLED").waitlist(false).message("您已成功正式报名！").build());

        mockMvc.perform(post("/student/activity/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("报名成功"))
                .andExpect(jsonPath("$.data.status").value("ENROLLED"))
                .andExpect(jsonPath("$.data.waitlist").value(false));

        ArgumentCaptor<ActivitySignupDTO> captor = ArgumentCaptor.forClass(ActivitySignupDTO.class);
        verify(service).signup(captor.capture());
        assertEquals(1L, captor.getValue().getActivityId());
    }

    @Test
    @DisplayName("POST /student/activity/signup：名额已满时返回候补状态与排队序号")
    void signup_shouldReturnWaitlistResult() throws Exception {
        when(service.signup(any(ActivitySignupDTO.class))).thenReturn(ActivitySignupVO.builder()
                .status("WAITLIST").waitlist(true).queueNumber(3)
                .message("名额已满，已进入候补队列（当前排第 3 位）").build());

        mockMvc.perform(post("/student/activity/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 1}"))
                .andExpect(jsonPath("$.data.status").value("WAITLIST"))
                .andExpect(jsonPath("$.data.waitlist").value(true))
                .andExpect(jsonPath("$.data.queueNumber").value(3));
    }

    @Test
    @DisplayName("POST /student/activity/signup：缺少 activityId 触发 @NotNull 校验失败")
    void signup_shouldRejectMissingActivityId() throws Exception {
        mockMvc.perform(post("/student/activity/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(service, never()).signup(any(ActivitySignupDTO.class));
    }

    @Test
    @DisplayName("POST /student/activity/signup：重复报名时透传 code=400 业务提示")
    void signup_shouldPropagateDuplicateEnrollment() throws Exception {
        when(service.signup(any(ActivitySignupDTO.class)))
                .thenThrow(new BusinessException(400, "您已报名该活动"));

        mockMvc.perform(post("/student/activity/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("您已报名该活动"));
    }

    // ────────────────────────── 取消报名 ──────────────────────────

    @Test
    @DisplayName("POST /student/activity/cancel-signup：正式报名取消返回「已取消报名」")
    void cancel_shouldReturnServiceMessage() throws Exception {
        when(service.cancelSignup(1L)).thenReturn("已取消报名");

        mockMvc.perform(post("/student/activity/cancel-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("已取消报名"));

        verify(service).cancelSignup(1L);
    }

    @Test
    @DisplayName("POST /student/activity/cancel-signup：候补退队返回「已退出候补队列」")
    void cancel_shouldReturnWaitlistExitMessage() throws Exception {
        when(service.cancelSignup(2L)).thenReturn("已退出候补队列");

        mockMvc.perform(post("/student/activity/cancel-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 2}"))
                .andExpect(jsonPath("$.message").value("已退出候补队列"));
    }

    @Test
    @DisplayName("POST /student/activity/cancel-signup：无有效报名记录时透传 code=400")
    void cancel_shouldPropagateNoActiveRecord() throws Exception {
        when(service.cancelSignup(anyLong()))
                .thenThrow(new BusinessException(400, "未找到有效报名记录"));

        mockMvc.perform(post("/student/activity/cancel-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\": 1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未找到有效报名记录"));
    }

    // ────────────────────────── 扫码打卡 ──────────────────────────

    @Test
    @DisplayName("POST /student/activity/checkin：返回发放积分、当前余额与证书颁发标记")
    void checkin_shouldReturnAwardResult() throws Exception {
        when(service.checkin(any(ActivityCheckinDTO.class))).thenReturn(ActivityCheckinVO.builder()
                .pointsAwarded(15).currentPoints(195).certificateIssued(true)
                .checkinTime(LocalDateTime.of(2026, 3, 7, 17, 35, 12)).build());

        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(1L);
        dto.setNonce("NONCE_7A9F8B2C4D1E00");
        dto.setSign("SIGN_9A8B7C6D5E4F3A2B1C");
        dto.setCheckinLatitude(new BigDecimal("30.523418"));
        dto.setCheckinLongitude(new BigDecimal("114.362140"));

        mockMvc.perform(post("/student/activity/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("打卡签到成功"))
                .andExpect(jsonPath("$.data.pointsAwarded").value(15))
                .andExpect(jsonPath("$.data.currentPoints").value(195))
                .andExpect(jsonPath("$.data.certificateIssued").value(true));

        ArgumentCaptor<ActivityCheckinDTO> captor = ArgumentCaptor.forClass(ActivityCheckinDTO.class);
        verify(service).checkin(captor.capture());
        assertEquals("NONCE_7A9F8B2C4D1E00", captor.getValue().getNonce());
        assertEquals("SIGN_9A8B7C6D5E4F3A2B1C", captor.getValue().getSign());
    }

    @Test
    @DisplayName("POST /student/activity/checkin：nonce 或 sign 为空触发 @NotBlank 校验失败")
    void checkin_shouldRejectBlankSecurityParams() throws Exception {
        mockMvc.perform(post("/student/activity/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"nonce\":\"\",\"sign\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));

        verify(service, never()).checkin(any(ActivityCheckinDTO.class));
    }

    @Test
    @DisplayName("POST /student/activity/checkin：签到码失效时透传 code=400 提示")
    void checkin_shouldPropagateExpiredCodeMessage() throws Exception {
        when(service.checkin(any(ActivityCheckinDTO.class)))
                .thenThrow(new BusinessException(400, "签到码无效或已过期"));

        mockMvc.perform(post("/student/activity/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"nonce\":\"N\",\"sign\":\"S\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("签到码无效或已过期"));
    }

    @Test
    @DisplayName("POST /student/activity/checkin：超出签到范围时透传 code=400 提示")
    void checkin_shouldPropagateOutOfRangeMessage() throws Exception {
        when(service.checkin(any(ActivityCheckinDTO.class)))
                .thenThrow(new BusinessException(400, "超出活动签到范围"));

        mockMvc.perform(post("/student/activity/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"nonce\":\"N\",\"sign\":\"S\",\"checkinLatitude\":30.6,\"checkinLongitude\":114.3}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("超出活动签到范围"));
    }

    // ────────────────────────── 我的活动记录 ──────────────────────────

    @Test
    @DisplayName("GET /student/activity/my-list：分页参数与状态筛选正确传递")
    void myList_shouldPassPagingAndStatus() throws Exception {
        Page<MyActivityVO> page = new Page<>(2, 5, 0);
        page.setRecords(new ArrayList<>());
        when(service.myList(anyInt(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/student/activity/my-list")
                        .param("page", "2")
                        .param("size", "5")
                        .param("status", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).myList(2, 5, 4);
    }

    @Test
    @DisplayName("GET /student/activity/my-list：不传状态时按 null 查询（合并正式与候补）")
    void myList_shouldAllowNullStatus() throws Exception {
        Page<MyActivityVO> page = new Page<>(1, 10, 0);
        page.setRecords(new ArrayList<>());
        when(service.myList(anyInt(), anyInt(), isNull())).thenReturn(page);

        mockMvc.perform(get("/student/activity/my-list"))
                .andExpect(status().isOk());

        verify(service).myList(1, 10, null);
    }
}
