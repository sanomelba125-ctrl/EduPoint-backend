package com.campus.modules.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.activity.service.ActivityService;
import com.campus.modules.admin.controller.ExportController;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("F10: 报表导出控制器 (ExportController) 导出边界与防御测试")
class ExportControllerTest {

    private ActivityService activityService;
    private StudentActivityMapper studentActivityMapper;
    private StudentPointRecordMapper pointRecordMapper;
    private StudentMapper studentMapper;
    private ExportController exportController;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), StudentPointRecord.class);
    }

    @BeforeEach
    void setUp() {
        activityService = mock(ActivityService.class);
        studentActivityMapper = mock(StudentActivityMapper.class);
        pointRecordMapper = mock(StudentPointRecordMapper.class);
        studentMapper = mock(StudentMapper.class);

        exportController = new ExportController(
                activityService,
                studentActivityMapper,
                pointRecordMapper,
                studentMapper
        );
    }

    @Test
    @DisplayName("F10: exportPoints 导出积分明细必须携带 LIMIT 5000，防止 4.9 万条全表无界拉取导致 OOM")
    void testExportPoints_CarriesLimit5000() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(pointRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        exportController.exportPoints(response);

        ArgumentCaptor<LambdaQueryWrapper<StudentPointRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(pointRecordMapper, times(1)).selectList(captor.capture());

        LambdaQueryWrapper<StudentPointRecord> wrapper = captor.getValue();
        assertNotNull(wrapper);
        String sqlSegment = wrapper.getSqlSegment();
        String customSqlSegment = wrapper.getCustomSqlSegment();
        // 验证带有 LIMIT 5000 的 last 语句
        assertTrue((sqlSegment != null && sqlSegment.contains("LIMIT 5000"))
                || (customSqlSegment != null && customSqlSegment.contains("LIMIT 5000"))
                || wrapper.getTargetSql().contains("LIMIT 5000"), "积分流水导出必须限制 LIMIT 5000");
    }

    @Test
    @DisplayName("F10: exportActivityRecords 采用 selectExportList(..., 5000) 绕过 maxLimit(100L) 截断")
    void testExportActivityRecords_BypassesMaxLimit100() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ActivityRecordVO record = new ActivityRecordVO();
        record.setId(1L);
        record.setStudentNo("20230101");
        record.setStudentName("张同学");
        record.setActivityTitle("雷锋月志愿服务");
        record.setStatus(2);
        record.setSignupTime(LocalDateTime.now().minusHours(2));
        record.setPointsAwarded(10);

        when(studentActivityMapper.selectExportList(eq("张同学"), eq("20230101"), eq("雷锋月"), eq(2), eq(5000)))
                .thenReturn(List.of(record));

        exportController.exportActivityRecords("张同学", "20230101", "雷锋月", 2, response);

        // 验证调用的是 selectExportList 带 limit 5000，绝不调用被截断为 100 条的 selectAdminPage
        verify(studentActivityMapper, times(1)).selectExportList("张同学", "20230101", "雷锋月", 2, 5000);
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
