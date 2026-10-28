package com.campus.modules.mall;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.service.StudentGiftService;
import com.campus.modules.mall.task.GiftOrderTimeoutScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 超时关单兜底任务单测。
 *
 * <p>验证「不依赖 MQ」的关单兜底：扫到的超时待提货订单必须逐笔走与死信消费完全相同的
 * {@code expireOrder} 路径，单笔异常不影响其余订单，查询异常不得让调度线程崩溃。
 */
@ExtendWith(MockitoExtension.class)
public class GiftOrderTimeoutSchedulerTest {

    @Mock
    private StudentGiftService studentGiftService;

    private GiftOrderTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new GiftOrderTimeoutScheduler(studentGiftService);
        ReflectionTestUtils.setField(scheduler, "orderTimeoutMs", 86_400_000L);
    }

    @Test
    @DisplayName("扫到超时待提货订单：逐笔执行统一关单路径（退分+回补库存+通知）")
    void shouldCloseEveryExpiredOrder() {
        when(studentGiftService.list(any(Wrapper.class))).thenReturn(List.of(order(1L), order(2L), order(3L)));

        scheduler.closeExpiredPendingOrders();

        verify(studentGiftService, times(1)).expireOrder(1L);
        verify(studentGiftService, times(1)).expireOrder(2L);
        verify(studentGiftService, times(1)).expireOrder(3L);
    }

    @Test
    @DisplayName("没有超时订单时保持静默，不产生任何关单调用")
    void shouldDoNothingWhenNoExpiredOrder() {
        when(studentGiftService.list(any(Wrapper.class))).thenReturn(List.of());

        scheduler.closeExpiredPendingOrders();

        verify(studentGiftService, never()).expireOrder(anyLong());
    }

    @Test
    @DisplayName("单笔关单抛异常不影响同批其余订单")
    void shouldContinueWhenOneOrderFails() {
        when(studentGiftService.list(any(Wrapper.class))).thenReturn(List.of(order(11L), order(12L)));
        doThrow(new RuntimeException("dead lock")).when(studentGiftService).expireOrder(11L);

        scheduler.closeExpiredPendingOrders();

        verify(studentGiftService, times(1)).expireOrder(12L);
    }

    @Test
    @DisplayName("扫描查询异常时本轮安全退出，不让定时任务线程崩溃")
    void shouldSwallowQueryFailure() {
        when(studentGiftService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> scheduler.closeExpiredPendingOrders());
        verify(studentGiftService, never()).expireOrder(anyLong());
    }

    private StudentGift order(Long id) {
        StudentGift order = new StudentGift();
        order.setId(id);
        order.setOrderNo("ORD-" + id);
        return order;
    }
}
