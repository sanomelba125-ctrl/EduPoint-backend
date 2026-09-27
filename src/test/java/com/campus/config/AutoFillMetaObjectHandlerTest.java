package com.campus.config;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.modules.activity.entity.Activity;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

@DisplayName("配置模块：MyBatis-Plus 审计字段自动填充测试 (P4-5)")
class AutoFillMetaObjectHandlerTest {

    private AutoFillMetaObjectHandler handler;
    private MockedStatic<StpUtil> stpUtil;

    @BeforeEach
    void setUp() {
        handler = new AutoFillMetaObjectHandler();
        stpUtil = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    @DisplayName("insertFill: 自动填充 createTime, updateTime 及当前操作人 ID")
    void insertFill_shouldPopulateTimeAndOperator() {
        stpUtil.when(StpUtil::isLogin).thenReturn(true);
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin_88");

        Activity activity = new Activity();
        MetaObject metaObject = SystemMetaObject.forObject(activity);

        handler.insertFill(metaObject);

        assertNotNull(activity.getCreateTime());
        assertNotNull(activity.getUpdateTime());
        assertEquals(88L, activity.getCreatedBy());
        assertEquals(88L, activity.getUpdatedBy());
    }

    @Test
    @DisplayName("insertFill: 如果业务层已经显式设置了 createTime，则不强制覆盖")
    void insertFill_shouldNotOverwriteExistingCreateTime() {
        stpUtil.when(StpUtil::isLogin).thenReturn(false);

        LocalDateTime explicitTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        Activity activity = new Activity();
        activity.setCreateTime(explicitTime);
        MetaObject metaObject = SystemMetaObject.forObject(activity);

        handler.insertFill(metaObject);

        assertEquals(explicitTime, activity.getCreateTime());
        assertNotNull(activity.getUpdateTime());
        assertNull(activity.getCreatedBy());
    }

    @Test
    @DisplayName("updateFill: 自动更新 updateTime 与 updatedBy")
    void updateFill_shouldUpdateTimestampAndOperator() {
        stpUtil.when(StpUtil::isLogin).thenReturn(true);
        stpUtil.when(StpUtil::getLoginIdAsString).thenReturn("student_99");

        Activity activity = new Activity();
        MetaObject metaObject = SystemMetaObject.forObject(activity);

        handler.updateFill(metaObject);

        assertNotNull(activity.getUpdateTime());
        assertEquals(99L, activity.getUpdatedBy());
    }
}
