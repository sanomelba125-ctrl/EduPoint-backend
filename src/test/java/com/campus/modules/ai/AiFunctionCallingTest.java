package com.campus.modules.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.ActivityWaitlistMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import com.campus.modules.ai.agent.tool.*;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.entity.QaKnowledge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI Function Calling 工具全套单元测试
 * 验证 5 大原子工具、Schema 合规性、IDOR 越权防护与工具注册中心调度
 */
class AiFunctionCallingTest {

    private StudentMapper studentMapper;
    private StudentPointRecordMapper pointRecordMapper;
    private StudentGiftMapper studentGiftMapper;
    private GiftMapper giftMapper;
    private StudentActivityMapper studentActivityMapper;
    private ActivityMapper activityMapper;
    private ActivityWaitlistMapper waitlistMapper;
    private CertificateMapper certificateMapper;
    private QaKnowledgeService qaKnowledgeService;

    private QueryPointsTool queryPointsTool;
    private QueryOrdersTool queryOrdersTool;
    private QueryActivitiesTool queryActivitiesTool;
    private QueryCertificatesTool queryCertificatesTool;
    private SearchKnowledgeTool searchKnowledgeTool;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        studentMapper = mock(StudentMapper.class);
        pointRecordMapper = mock(StudentPointRecordMapper.class);
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftMapper = mock(GiftMapper.class);
        studentActivityMapper = mock(StudentActivityMapper.class);
        activityMapper = mock(ActivityMapper.class);
        waitlistMapper = mock(ActivityWaitlistMapper.class);
        certificateMapper = mock(CertificateMapper.class);
        qaKnowledgeService = mock(QaKnowledgeService.class);

        queryPointsTool = new QueryPointsTool(studentMapper, pointRecordMapper);
        queryOrdersTool = new QueryOrdersTool(studentGiftMapper, giftMapper);
        queryActivitiesTool = new QueryActivitiesTool(studentActivityMapper, activityMapper, waitlistMapper);
        queryCertificatesTool = new QueryCertificatesTool(certificateMapper);
        searchKnowledgeTool = new SearchKnowledgeTool(qaKnowledgeService);

        toolRegistry = new ToolRegistry(List.of(
                queryPointsTool, queryOrdersTool, queryActivitiesTool, queryCertificatesTool,
                searchKnowledgeTool
        ));
        toolRegistry.init();
    }

    @Test
    @DisplayName("测试工具契约: 5 个工具均生成符合 OpenAI / SenseNova 协议的 Function Schema")
    void testToolDefinitionsCompliantWithOpenAiSpec() {
        JSONArray definitions = toolRegistry.getOpenAiToolDefinitions();
        assertNotNull(definitions);
        assertEquals(5, definitions.size(), "必须注册 5 个原子只读工具");

        for (int i = 0; i < definitions.size(); i++) {
            JSONObject tool = definitions.getJSONObject(i);
            assertEquals("function", tool.getString("type"));
            JSONObject func = tool.getJSONObject("function");
            assertNotNull(func);
            assertTrue(func.containsKey("name"));
            assertTrue(func.containsKey("description"));
            assertTrue(func.containsKey("parameters"));

            JSONObject params = func.getJSONObject("parameters");
            assertEquals("object", params.getString("type"));
            assertTrue(params.containsKey("properties"));
        }
    }

    @Test
    @DisplayName("测试公开/个人工具分离: 仅公开工具对外暴露，个人工具不向大模型暴露")
    void testPublicToolSeparation() {
        JSONArray publicDefs = toolRegistry.getPublicToolDefinitions();
        assertNotNull(publicDefs);
        assertEquals(1, publicDefs.size(), "公开工具应为 1 个: search_platform_knowledge");

        // 验证公开工具名称
        assertTrue(toolRegistry.isPublicTool("search_platform_knowledge"));

        // 验证个人工具不对外暴露
        assertFalse(toolRegistry.isPublicTool("query_my_points"));
        assertFalse(toolRegistry.isPublicTool("query_my_orders"));
        assertFalse(toolRegistry.isPublicTool("query_my_activities"));
        assertFalse(toolRegistry.isPublicTool("query_my_certificates"));
    }

    @Test
    @DisplayName("测试 QueryPointsTool: 校验未登录拦截与个人积分流水提取")
    void testQueryPointsTool() {
        // 1. 未登录拦截 (IDOR 防护)
        String unauthResult = queryPointsTool.execute(null, new JSONObject());
        JSONObject unauthJson = JSON.parseObject(unauthResult);
        assertFalse(unauthJson.getBooleanValue("success"));
        assertTrue(unauthJson.getString("error").contains("登录"));

        // 2. 正常查询
        Student mockStudent = new Student();
        mockStudent.setId(101L);
        mockStudent.setName("张三");
        mockStudent.setStudentNo("2024001");
        mockStudent.setCurrentPoints(350);
        mockStudent.setTotalPoints(500);
        when(studentMapper.selectById(101L)).thenReturn(mockStudent);

        StudentPointRecord rec1 = new StudentPointRecord();
        rec1.setType(1);
        rec1.setChangePoints(50);
        rec1.setAfterPoints(350);
        rec1.setDescription("讲座打卡奖励");
        rec1.setCreateTime(LocalDateTime.now());
        when(pointRecordMapper.selectList(any())).thenReturn(List.of(rec1));

        String result = queryPointsTool.execute(101L, JSON.parseObject("{\"limit\": 5}"));
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(350, json.getIntValue("currentAvailablePoints"));
        assertEquals(500, json.getIntValue("historicalTotalPoints"));
        assertEquals(1, json.getJSONArray("recentTransactions").size());
    }

    @Test
    @DisplayName("测试 QueryOrdersTool: 校验待核销提货码与商品信息获取")
    void testQueryOrdersTool() {
        StudentGift order = new StudentGift();
        order.setOrderNo("ORD20260315001");
        order.setGiftId(201L);
        order.setQuantity(1);
        order.setPointsCost(80);
        order.setPickupCode("PICKUP-8899");
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());

        when(studentGiftMapper.selectList(any())).thenReturn(List.of(order));
        when(studentGiftMapper.selectCount(any())).thenReturn(1L);

        Gift mockGift = new Gift();
        mockGift.setId(201L);
        mockGift.setName("校园定制保温杯");
        mockGift.setDescription("304不锈钢");
        when(giftMapper.selectById(201L)).thenReturn(mockGift);

        String result = queryOrdersTool.execute(101L, JSON.parseObject("{\"status\": 1}"));
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("pendingPickupCount"));
        JSONArray orders = json.getJSONArray("orders");
        assertEquals(1, orders.size());
        JSONObject o0 = orders.getJSONObject(0);
        assertEquals("PICKUP-8899", o0.getString("pickupCode"));
        assertEquals("校园定制保温杯", o0.getString("giftName"));
        assertTrue(o0.getString("statusText").contains("待核销"));
    }

    @Test
    @DisplayName("测试 QueryActivitiesTool: 校验已报名活动与候补排位顺位计算")
    void testQueryActivitiesTool() {
        StudentActivity sa = new StudentActivity();
        sa.setActivityId(301L);
        sa.setStatus(1); // 1-已报名
        sa.setSignupTime(LocalDateTime.now());
        when(studentActivityMapper.selectList(any())).thenReturn(List.of(sa));

        Activity mockAct = new Activity();
        mockAct.setId(301L);
        mockAct.setTitle("科技创新大赛宣讲会");
        mockAct.setPoints(30);
        mockAct.setLocationName("学生活动中心101");
        when(activityMapper.selectById(301L)).thenReturn(mockAct);

        // 模拟候补记录
        ActivityWaitlist wl = new ActivityWaitlist();
        wl.setActivityId(302L);
        wl.setStudentId(101L);
        wl.setQueueNumber(5);
        wl.setWaitTime(LocalDateTime.now());
        when(waitlistMapper.selectList(any())).thenReturn(List.of(wl));
        when(waitlistMapper.selectCount(any())).thenReturn(3L); // 前方排位有效顺位第 3

        Activity mockAct2 = new Activity();
        mockAct2.setId(302L);
        mockAct2.setTitle("热门羽毛球友谊赛");
        mockAct2.setPoints(20);
        when(activityMapper.selectById(302L)).thenReturn(mockAct2);

        String result = queryActivitiesTool.execute(101L, new JSONObject());
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        JSONArray acts = json.getJSONArray("activities");
        assertEquals(2, acts.size());

        JSONObject a0 = acts.getJSONObject(0);
        assertEquals("科技创新大赛宣讲会", a0.getString("activityTitle"));
        assertTrue(a0.getString("statusText").contains("已成功报名"));

        JSONObject a1 = acts.getJSONObject(1);
        assertEquals("热门羽毛球友谊赛", a1.getString("activityTitle"));
        assertTrue(a1.getString("statusText").contains("顺位第 3 位"));
    }

    @Test
    @DisplayName("测试 QueryActivitiesTool: 活动已被删除的报名/候补记录不再渲染成空标题卡片")
    void testQueryActivitiesToolSkipsOrphanRecords() {
        // 报名表与候补表不会随活动删除级联清理，回表为 null 是常态脏数据
        StudentActivity orphanSignup = new StudentActivity();
        orphanSignup.setId(501L);
        orphanSignup.setActivityId(901L);
        orphanSignup.setStatus(1);
        orphanSignup.setSignupTime(LocalDateTime.now());
        when(studentActivityMapper.selectList(any())).thenReturn(List.of(orphanSignup));

        ActivityWaitlist orphanWait = new ActivityWaitlist();
        orphanWait.setId(502L);
        orphanWait.setActivityId(902L);
        orphanWait.setStatus(1);
        orphanWait.setQueueNumber(1);
        orphanWait.setWaitTime(LocalDateTime.now());
        when(waitlistMapper.selectList(any())).thenReturn(List.of(orphanWait));

        when(activityMapper.selectById(any())).thenReturn(null);

        String result = queryActivitiesTool.execute(101L, new JSONObject());
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getJSONArray("activities").size(), "孤儿记录必须全部丢弃");
        assertEquals(0, json.getIntValue("returnedCount"));
    }

    @Test
    @DisplayName("测试 QueryCertificatesTool: 校验结项荣誉证书及防伪编号返回")
    void testQueryCertificatesTool() {
        CertificateMyVO cert = new CertificateMyVO();
        cert.setId(401L);
        cert.setCertificateNo("CERT-2026-ACT2-XYZ987");
        cert.setTitle("创新创业实践结项证书");
        cert.setActivityTitle("双创实践训练营");
        cert.setIssuer("校团委");
        cert.setIssueTime(LocalDateTime.now());
        cert.setStatus(1);

        when(certificateMapper.selectMyList(101L)).thenReturn(List.of(cert));

        String result = queryCertificatesTool.execute(101L, new JSONObject());
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("totalCertificateCount"));
        JSONArray certs = json.getJSONArray("certificates");
        assertEquals(1, certs.size());
        assertEquals("CERT-2026-ACT2-XYZ987", certs.getJSONObject(0).getString("certificateNo"));
        assertTrue(certs.getJSONObject(0).getString("statusText").contains("有效"));
    }

    @Test
    @DisplayName("测试 SearchKnowledgeTool: 校验关键词检索官方标准问答")
    void testSearchKnowledgeTool() {
        QaKnowledge item = new QaKnowledge();
        item.setId(1L);
        item.setCategory("SCORE");
        item.setQuestion("如何获得活动积分？");
        item.setAnswer("报名参加校园活动，现场完成动态二维码扫码打卡后自动发放。");

        when(qaKnowledgeService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(item));

        String result = searchKnowledgeTool.execute(101L, JSON.parseObject("{\"keyword\": \"积分\"}"));
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("matchedCount"));
        assertEquals("如何获得活动积分？", json.getJSONArray("matchedFaqs").getJSONObject(0).getString("question"));
    }

    @Test
    @DisplayName("测试 ToolRegistry 调度中心: 正常调度与未知工具兜底容错")
    void testToolRegistryExecution() {
        // 1. 成功调度
        when(studentMapper.selectById(101L)).thenReturn(new Student());
        ToolExecutionRecord record = toolRegistry.executeTool("call_123", "query_my_points", "{\"limit\": 3}", 101L);

        assertNotNull(record);
        assertEquals("call_123", record.getToolCallId());
        assertEquals("query_my_points", record.getToolName());
        assertEquals("SUCCESS", record.getStatus());
        assertTrue(record.getExecutionTimeMs() >= 0);

        // 2. 未知工具容错
        ToolExecutionRecord unknownRecord = toolRegistry.executeTool("call_999", "unknown_function", "{}", 101L);
        assertNotNull(unknownRecord);
        assertEquals("FAILED", unknownRecord.getStatus());
        assertTrue(unknownRecord.getResultFactJson().contains("未找到指定的工具能力"));
    }
}
