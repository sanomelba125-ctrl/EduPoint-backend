package com.campus.modules.activity;

import com.campus.modules.activity.entity.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校园活动实体状态与业务约束单元测试
 */
class ActivityEntityTest {

    @Test
    @DisplayName("测试活动实体属性设值与默认业务规则")
    void testActivityEntityRules() {
        Activity act = new Activity();
        act.setId(101L);
        act.setTitle("AI前沿讲座");
        act.setCategory("ACADEMIC");
        act.setPoints(30);
        act.setRegistrationLimit(100);
        act.setParticipantCount(85);
        act.setStatus(1); // 进行中
        act.setLocationRadius(300);
        act.setLatitude(new BigDecimal("30.524105"));
        act.setLongitude(new BigDecimal("114.364210"));

        assertEquals(101L, act.getId());
        assertEquals("AI前沿讲座", act.getTitle());
        assertEquals("ACADEMIC", act.getCategory());
        assertEquals(30, act.getPoints());
        assertTrue(act.getParticipantCount() <= act.getRegistrationLimit());
        assertEquals(300, act.getLocationRadius());
        assertEquals(1, act.getStatus());
    }

    @Test
    @DisplayName("测试活动时间与生命周期逻辑约束")
    void testActivityLifecycleConstraint() {
        Activity act = new Activity();
        LocalDateTime now = LocalDateTime.now();
        act.setStartTime(now.minusHours(1));
        act.setEndTime(now.plusHours(2));

        assertTrue(act.getStartTime().isBefore(act.getEndTime()));
    }
}
