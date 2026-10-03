package com.campus;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.campus.config.QiniuProperties;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.certificate.service.impl.CertificateRenderServiceImpl;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("模块 8：电子荣誉证书渲染引擎 (CertificateRenderService) 单元测试")
class CertificateRenderServiceImplTest {

    @Mock
    private CertificateMapper certificateMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ActivityMapper activityMapper;

    private QiniuProperties qiniuProperties;
    private CertificateRenderServiceImpl renderService;
    private AutoCloseable mocks;

    private static final String TEST_CERT_NO = "CERT-TEST-RENDER-001";

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Certificate.class);
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        qiniuProperties = new QiniuProperties(); // 未配置七牛云，默认走本地降级存储
        renderService = new CertificateRenderServiceImpl(
                certificateMapper,
                studentMapper,
                activityMapper,
                qiniuProperties
        );
        cleanTestFile();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanTestFile();
        mocks.close();
    }

    private void cleanTestFile() {
        try {
            Path path = Paths.get("uploads", "certificates", TEST_CERT_NO + ".png");
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("renderToPngBytesByNo: 证书不存在时返回 null")
    void renderToPngBytesByNo_notFound_shouldReturnNull() {
        when(certificateMapper.selectOne(any())).thenReturn(null);

        byte[] bytes = renderService.renderToPngBytesByNo("NON_EXISTENT");
        assertNull(bytes);
    }

    @Test
    @DisplayName("renderToPngBytesByNo: 真实合成羊皮纸背景、公章与防伪二维码，验证 PNG 魔数")
    void renderToPngBytesByNo_success_shouldGenerateValidPng() {
        Certificate cert = new Certificate();
        cert.setId(1001L);
        cert.setCertificateNo(TEST_CERT_NO);
        cert.setStudentId(2001L);
        cert.setActivityId(3001L);
        cert.setTitle("校园十佳志愿者荣誉证书");
        cert.setIssuer("共青团校园实训委员会");
        cert.setIssueTime(LocalDateTime.of(2026, 9, 27, 10, 0));
        cert.setStatus(1);

        Student student = new Student();
        student.setId(2001L);
        student.setName("张三");
        student.setStudentNo("20260901001");
        student.setCollege("计算机科学与技术学院");

        Activity activity = new Activity();
        activity.setId(3001L);
        activity.setTitle("迎新志愿服务活动");

        when(certificateMapper.selectOne(any())).thenReturn(cert);
        when(studentMapper.selectById(2001L)).thenReturn(student);
        when(activityMapper.selectById(3001L)).thenReturn(activity);

        byte[] bytes = renderService.renderToPngBytesByNo(TEST_CERT_NO);

        assertNotNull(bytes);
        assertTrue(bytes.length > 1000, "生成的 PNG 证书文件应大于 1KB");

        // 验证 PNG 文件签名 (Magic Number): 89 50 4E 47 0D 0A 1A 0A
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals('P', (char) bytes[1]);
        assertEquals('N', (char) bytes[2]);
        assertEquals('G', (char) bytes[3]);

        // 验证已自动落盘至本地缓存
        Path cachedFile = Paths.get("uploads", "certificates", TEST_CERT_NO + ".png");
        assertTrue(Files.exists(cachedFile), "本地磁盘缓存文件应存在");
    }

    @Test
    @DisplayName("renderAndUpload: 未配置七牛云时降级保存本地，并回写 file_url 与数据库")
    void renderAndUpload_localFallback_shouldUpdateCertificateFileUrl() {
        Certificate cert = new Certificate();
        cert.setId(1002L);
        cert.setCertificateNo(TEST_CERT_NO);
        cert.setStudentId(2002L);
        cert.setActivityId(3002L);
        cert.setTitle("科技创新大赛一等奖");
        cert.setIssuer("教务处");
        cert.setIssueTime(LocalDateTime.now());
        cert.setStatus(1);

        when(certificateMapper.selectById(1002L)).thenReturn(cert);
        when(studentMapper.selectById(2002L)).thenReturn(null);
        when(activityMapper.selectById(3002L)).thenReturn(null);

        String fileUrl = renderService.renderAndUpload(1002L);

        assertNotNull(fileUrl);
        assertEquals("/api/certificate/file/" + TEST_CERT_NO + ".png", fileUrl);
        assertEquals(fileUrl, cert.getFileUrl());
        verify(certificateMapper).updateById(cert);
    }

    @Test
    @DisplayName("renderAndUpload: ID 为空或不存在时安全返回 null")
    void renderAndUpload_nullOrNotFound_shouldReturnNull() {
        assertNull(renderService.renderAndUpload(null));

        when(certificateMapper.selectById(9999L)).thenReturn(null);
        assertNull(renderService.renderAndUpload(9999L));
        verify(certificateMapper, never()).updateById(any(Certificate.class));
    }
}
