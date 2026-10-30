package com.campus;

import com.campus.modules.activity.dto.MyActivityVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.enums.StudentActivityStatus;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.enums.OrderStatus;
import com.campus.modules.student.entity.Student;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：所有带 {@code status} 的实体与 VO，序列化后必须真的出现 {@code status} 字段。
 *
 * <p>背景（2026-10-30）：7 个实体原来都写了一个「参数是枚举」的 {@code setStatus} 重载并加了
 * {@code @JsonIgnore}。Jackson 把两个 setter 视为同一属性 {@code status} 的冲突访问器，
 * <b>给其中一个打 {@code @JsonIgnore} 会让整个属性连 getter 一起被丢弃</b>。</p>
 *
 * <p>实际后果分两批暴露：</p>
 * <ul>
 *   <li>第一批（活动域）：{@code GET /student/activity/my-list} 返回的记录里没有 {@code status}，
 *       而移动端 {@code activity.ts} 靠它区分已报名(1)/已完成(2)/候补中(4) 并过滤已取消(3)；</li>
 *   <li>第二批（商城/证书/学生域）：列表接口走 VO 暂时掩盖了问题，但只要哪条链路直接序列化实体
 *       （缓存、导出、开放接口），{@code status} 一样会消失。</li>
 * </ul>
 *
 * <p>所以这里做两件事：① 逐个实体断言序列化结果含 {@code "status":1}；
 * ② 用反射断言<b>没有任何实体再保留非 Integer 的 setStatus 重载</b>——只测 JSON 不够，
 * 因为坏代码可能再加回来而当前用例仍然通过。</p>
 */
class EntityStatusSerializationTest {

    /** 参与序列化的实体清单：新增带 status 的实体时在这里补一行 */
    private static final List<Class<?>> STATUS_ENTITIES = List.of(
            Activity.class,
            ActivityWaitlist.class,
            StudentActivity.class,
            Gift.class,
            StudentGift.class,
            Certificate.class,
            Student.class);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("7 个实体：序列化后必须都有 status，且不得暴露内部枚举视图 statusEnum")
    void everyEntityMustSerializeStatus() throws Exception {
        for (Class<?> clazz : STATUS_ENTITIES) {
            Object entity = clazz.getDeclaredConstructor().newInstance();
            Method setter = clazz.getMethod("setStatus", Integer.class);
            setter.invoke(entity, 1);

            String json = mapper.writeValueAsString(entity);

            assertTrue(json.contains("\"status\":1"),
                    clazz.getSimpleName() + " 的 status 被整个丢弃了（检查是否有枚举 setStatus 重载 + @JsonIgnore）: " + json);
            assertFalse(json.contains("statusEnum"),
                    clazz.getSimpleName() + " 不应把内部枚举视图 statusEnum 暴露给前端: " + json);
        }
    }

    @Test
    @DisplayName("根因防线：任何实体都不得再保留「参数是枚举」的 setStatus 重载")
    void noEntityKeepsEnumStatusOverload() {
        for (Class<?> clazz : STATUS_ENTITIES) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"setStatus".equals(method.getName())) {
                    continue;
                }
                assertEquals(1, method.getParameterCount(),
                        clazz.getSimpleName() + "#setStatus 参数个数异常");
                assertEquals(Integer.class, method.getParameterTypes()[0],
                        clazz.getSimpleName() + " 又出现了非 Integer 的 setStatus 重载（"
                                + method.getParameterTypes()[0].getSimpleName()
                                + "）：Jackson 会把它当成 status 的冲突 setter，一旦加 @JsonIgnore，"
                                + "整个 status 属性连 getter 一起消失。请改用 setStatusEnum(...)");
            }
        }
    }

    @Test
    @DisplayName("MyActivityVO（继承 StudentActivity）：status 同样必须序列化")
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
    @DisplayName("商城订单：数字 status 能反序列化回来，setStatusEnum 仍可把枚举写进 status")
    void studentGiftStatusRoundTrip() throws Exception {
        StudentGift order = mapper.readValue("{\"id\":1,\"status\":1}", StudentGift.class);
        assertEquals(1, order.getStatus());

        order.setStatusEnum(OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED.getCode(), order.getStatus());
        assertTrue(mapper.writeValueAsString(order).contains("\"status\":" + OrderStatus.CANCELLED.getCode()));
    }

    @Test
    @DisplayName("活动记录：setStatusEnum 仍可写枚举，且序列化结果跟着变")
    void studentActivityStatusRoundTrip() throws Exception {
        StudentActivity record = mapper.readValue("{\"id\":1,\"status\":2}", StudentActivity.class);
        assertEquals(2, record.getStatus());

        record.setStatusEnum(StudentActivityStatus.CANCELLED);
        assertEquals(StudentActivityStatus.CANCELLED.getCode(), record.getStatus());
    }
}
