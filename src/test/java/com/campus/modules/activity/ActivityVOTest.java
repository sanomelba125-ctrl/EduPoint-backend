package com.campus.modules.activity;

import com.campus.modules.activity.dto.ActivityVO;
import com.campus.modules.activity.entity.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("模块 2：ActivityVO 隔离与契约测试")
class ActivityVOTest {

    @Test
    @DisplayName("fromEntity: null 实体安全返回 null")
    void fromEntity_null_shouldReturnNull() {
        assertNull(ActivityVO.fromEntity(null));
    }

    @Test
    @DisplayName("fromEntity: 屏蔽底层 version, deleted, createdBy, updatedBy 字段，并转换分类名称")
    void fromEntity_success_shouldMapAndIsolateFields() {
        Activity a = new Activity();
        a.setId(99L);
        a.setTitle("开源技术讲座");
        a.setCategory("ACADEMIC");
        a.setDescription("深度探讨分布式高可用架构");
        a.setRuleDescription("现场打卡签到获得积分");
        a.setCoverImage("https://cdn.example.com/cover.jpg");
        a.setPoints(20);
        a.setStartTime(LocalDateTime.of(2026, 9, 28, 14, 0));
        a.setEndTime(LocalDateTime.of(2026, 9, 28, 16, 0));
        a.setRegistrationLimit(100);
        a.setParticipantCount(45);
        a.setWaitlistEnabled(1);
        a.setLocationName("第三教学楼301");
        a.setLocationAddress("学子路18号");
        a.setLongitude(new BigDecimal("116.4074"));
        a.setLatitude(new BigDecimal("39.9042"));
        a.setLocationRadius(500);
        a.setLocationCheckEnabled(1);
        a.setCertificateEnabled(1);
        a.setCertificateTitle("技术交流荣誉证书");
        a.setCertificateIssuer("信息工程学院");
        a.setStatus(1);
        a.setCreateTime(LocalDateTime.of(2026, 9, 20, 10, 0));
        a.setMyEnrollmentStatus("ENROLLED");
        a.setMyQueueNumber(null);

        // 敏感内部持久化字段
        a.setVersion(3);
        a.setDeleted(0);
        a.setCreatedBy(1L);
        a.setUpdatedBy(2L);

        ActivityVO vo = ActivityVO.fromEntity(a);

        assertNotNull(vo);
        assertEquals(99L, vo.getId());
        assertEquals("开源技术讲座", vo.getTitle());
        assertEquals("ACADEMIC", vo.getCategory());
        assertEquals("学术讲座", vo.getCategoryName());
        assertEquals("深度探讨分布式高可用架构", vo.getDescription());
        assertEquals(20, vo.getPoints());
        assertEquals(100, vo.getRegistrationLimit());
        assertEquals(45, vo.getParticipantCount());
        assertEquals(1, vo.getWaitlistEnabled());
        assertEquals("ENROLLED", vo.getMyEnrollmentStatus());

        // 验证分类映射
        a.setCategory("VOLUNTEER");
        assertEquals("志愿服务", ActivityVO.fromEntity(a).getCategoryName());

        a.setCategory("SPORTS");
        assertEquals("文体竞技", ActivityVO.fromEntity(a).getCategoryName());

        a.setCategory("PRACTICE");
        assertEquals("社会实践", ActivityVO.fromEntity(a).getCategoryName());

        a.setCategory("OTHER");
        assertEquals("其他活动", ActivityVO.fromEntity(a).getCategoryName());

        a.setCategory(null);
        assertEquals("其他活动", ActivityVO.fromEntity(a).getCategoryName());
    }
}
