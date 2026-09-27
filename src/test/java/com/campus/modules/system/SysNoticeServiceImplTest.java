package com.campus.modules.system;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.campus.common.auth.AuthUtil;
import com.campus.modules.system.entity.SysNotice;
import com.campus.modules.system.mapper.SysNoticeMapper;
import com.campus.modules.system.mapper.SysNoticeReadMapper;
import com.campus.modules.system.service.impl.SysNoticeServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P2-9 全校广播通知读写分离治理单元测试
 */
class SysNoticeServiceImplTest {

    private SysNoticeMapper sysNoticeMapper;
    private SysNoticeReadMapper sysNoticeReadMapper;
    private SysNoticeServiceImpl sysNoticeService;
    private MockedStatic<AuthUtil> authUtil;

    private static final Long STUDENT_ID = 2001L;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SysNotice.class);
    }

    @BeforeEach
    void setUp() {
        sysNoticeMapper = mock(SysNoticeMapper.class);
        sysNoticeReadMapper = mock(SysNoticeReadMapper.class);
        sysNoticeService = new SysNoticeServiceImpl(sysNoticeReadMapper);
        ReflectionTestUtils.setField(sysNoticeService, "baseMapper", sysNoticeMapper);

        authUtil = mockStatic(AuthUtil.class);
        authUtil.when(AuthUtil::isStudent).thenReturn(true);
        authUtil.when(AuthUtil::requireStudentId).thenReturn(STUDENT_ID);
    }

    @AfterEach
    void tearDown() {
        if (authUtil != null) {
            authUtil.close();
        }
    }

    @Test
    @DisplayName("P2-9: 学生将全校公共广播标记为已读时，仅更新 sys_notice_read，绝不修改 sys_notice 主表")
    void testMarkBroadcastAsReadIsolated() {
        Long noticeId = 99L;
        SysNotice broadcastNotice = new SysNotice();
        broadcastNotice.setId(noticeId);
        broadcastNotice.setReceiverType(2); // student
        broadcastNotice.setReceiverId(0L);  // 全校广播
        broadcastNotice.setIsRead(0);

        when(sysNoticeMapper.selectById(noticeId)).thenReturn(broadcastNotice);
        when(sysNoticeReadMapper.upsertReadRecord(eq(noticeId), eq(STUDENT_ID), eq(2))).thenReturn(1);

        boolean result = sysNoticeService.markAsRead(noticeId);

        assertTrue(result);
        // 验证仅操作了已读关联表
        verify(sysNoticeReadMapper, times(1)).upsertReadRecord(eq(noticeId), eq(STUDENT_ID), eq(2));
        // 严禁对 sys_notice 执行 updateById
        verify(sysNoticeMapper, never()).updateById(any(SysNotice.class));
    }

    @Test
    @DisplayName("P2-9: 学生删除全校公共广播时，仅向 sys_notice_read 写入软隐藏，绝不调用 removeById")
    void testDeleteBroadcastNoticeIsolated() {
        Long noticeId = 88L;
        SysNotice broadcastNotice = new SysNotice();
        broadcastNotice.setId(noticeId);
        broadcastNotice.setReceiverType(2);
        broadcastNotice.setReceiverId(0L);

        when(sysNoticeMapper.selectById(noticeId)).thenReturn(broadcastNotice);
        when(sysNoticeReadMapper.upsertDeletedRecord(eq(noticeId), eq(STUDENT_ID), eq(2))).thenReturn(1);

        boolean result = sysNoticeService.deleteNotice(noticeId);

        assertTrue(result);
        // 验证仅标记用户软删除
        verify(sysNoticeReadMapper, times(1)).upsertDeletedRecord(eq(noticeId), eq(STUDENT_ID), eq(2));
        // 严禁物理调用 removeById 造成公告全校蒸发
        verify(sysNoticeMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("P2-9: 学生删除属于自己的个人私信时，正常执行逻辑删除")
    void testDeletePrivateNotice() {
        Long noticeId = 77L;
        SysNotice privateNotice = new SysNotice();
        privateNotice.setId(noticeId);
        privateNotice.setReceiverType(2);
        privateNotice.setReceiverId(STUDENT_ID);

        when(sysNoticeMapper.selectById(noticeId)).thenReturn(privateNotice);
        when(sysNoticeMapper.deleteById(noticeId)).thenReturn(1);

        boolean result = sysNoticeService.deleteNotice(noticeId);

        assertTrue(result);
        verify(sysNoticeMapper, times(1)).deleteById(noticeId);
        verify(sysNoticeReadMapper, never()).upsertDeletedRecord(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("P2-9: 一键已读同时覆盖个人私信更新与全校广播已读关联插入")
    void testMarkAllAsRead() {
        when(sysNoticeMapper.update(isNull(), any())).thenReturn(1);
        when(sysNoticeReadMapper.markAllBroadcastAsRead(eq(STUDENT_ID), eq(2))).thenReturn(2);

        boolean result = sysNoticeService.markAllAsRead();

        assertTrue(result);
        verify(sysNoticeReadMapper, times(1)).markAllBroadcastAsRead(eq(STUDENT_ID), eq(2));
    }
}
