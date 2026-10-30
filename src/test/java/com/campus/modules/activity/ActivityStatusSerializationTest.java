package com.campus.modules.activity;

import com.campus.modules.activity.dto.MyActivityVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.enums.StudentActivityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：{@code status} 必须出现在接口返回的 JSON 里（2026-10-30）。
 *
 * <p>背景：三个实体原来都写了一个「参数是枚举」的 {@code setStatus} 重载并加了
 * {@code @JsonIgnore}。Jackson 把这两个 setter 视为同一属性 {@code status} 的冲突访问器，
 * 给其中一个打 {@code @JsonIgnore} 会让<b>整个属性连 getter 一起被丢弃</b>。
 * 线上表现：「我的活动」接口返回的每条记录都没有 {@code status} 字段，
 * 而移动端 {@code activity.ts} 正是靠它判断已报名(1)/已完成(2)/候补中(4) 并过滤已取消(3)。
 *
 * <p>所以这里直接对序列化结果做断言，而不是只测 getter——测 getter 是测不出这个坑的。
 */
class ActivityStatusSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("我的活动 VO：status 必须被序列化（丢了移动端就分不清已报名/候补中）")
    void myActivityVoMustExposeStatus() throws Exception {
        MyActivityVO vo = new MyActivityVO();
        vo.setId(1L);
        vo.setActivityId(2L);
        vo.setStatus(1);
        vo.setTitle("测试活动");

        String json = mapper.writeValueAsString(vo);

        assertTrue(json.contains("\"status\":1"), "status 丢失，移动端无法判断参与状态: " + json);
        assertFalse(json.contains("statusEnum"), "statusEnum 是内部枚举视图，不应出现在响应里: " + json);
    }

    @Test
    @DisplayName("活动与候补实体：status 同样必须被序列化")
    void activityAndWaitlistMustExposeStatus() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setStatus(1);
        assertTrue(mapper.writeValueAsString(activity).contains("\"status\":1"));

        ActivityWaitlist waitlist = new ActivityWaitlist();
        waitlist.setId(1L);
        waitlist.setStatus(2);
        assertTrue(mapper.writeValueAsString(waitlist).contains("\"status\":2"));
    }

    @Test
    @DisplayName("反序列化：数字 status 能读回，setStatusEnum 仍可把枚举写进 status")
    void statusMustRoundTrip() throws Exception {
        StudentActivity record = mapper.readValue("{\"id\":1,\"status\":2}", StudentActivity.class);
        assertEquals(2, record.getStatus());

        record.setStatusEnum(StudentActivityStatus.CANCELLED);
        assertEquals(StudentActivityStatus.CANCELLED.getCode(), record.getStatus());
    }
}
