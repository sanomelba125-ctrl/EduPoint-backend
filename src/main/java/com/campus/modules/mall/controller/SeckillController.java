package com.campus.modules.mall.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.common.annotation.RateLimit;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.campus.common.result.Result;
import com.campus.modules.mall.dto.GiftExchangeDTO;
import com.campus.modules.mall.dto.GiftExchangeVO;
import com.campus.modules.mall.dto.SeckillResultVO;
import com.campus.modules.mall.service.StudentGiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 秒杀高并发独立业务控制器 (P6-1 秒杀模块独立化与线程池资源隔离)
 * 具备: 滑动窗口限流前置拦截 + 专用隔离线程池 + 异步削峰排队 + 结果轮询
 */
@Slf4j
@Tag(name = "秒杀高并发兑换接口", description = "提供 Redis+Lua 内存预扣与 MQ 异步削峰的秒杀高可用通道")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final StudentGiftService studentGiftService;

    @Qualifier("seckillThreadPoolTaskExecutor")
    private final ThreadPoolTaskExecutor seckillThreadPoolTaskExecutor;

    /**
     * 秒杀高并发抢兑通道 (P1-1, P1-2, P1-6)
     */
    @Operation(summary = "秒杀抢兑下单 (异步削峰排队)")
    @RateLimit(key = "rate_limit:seckill:global", count = 2500, windowMs = 1000)
    @RateLimit(key = "rate_limit:seckill:user", count = 2, windowMs = 1000, limitType = RateLimit.LimitType.USER, message = "手速太快啦，请稍后重试！")
    @SaCheckRole(value = {UserRole.STUDENT}, mode = SaMode.OR)
    @PostMapping("/exchange")
    public CompletableFuture<Result<GiftExchangeVO>> seckillExchange(@Valid @RequestBody GiftExchangeDTO dto) {
        Long studentId = AuthUtil.requireStudentId();
        // 隔离线程池中执行预扣与 Outbox 投递，杜绝耗尽 Tomcat 业务线程
        return CompletableFuture.supplyAsync(
                () -> {
                    GiftExchangeVO vo = studentGiftService.seckillExchange(dto, studentId);
                    return Result.success("秒杀抢兑已受理，正在排队落库中", vo);
                },
                seckillThreadPoolTaskExecutor
        );
    }

    /**
     * 轮询秒杀下单处理结果。
     *
     * <p>返回三态而非「有/无订单」：PROCESSING 继续等、SUCCESS 展示提货码、
     * FAILED 明确告知失败原因。缺了 FAILED，业务校验失败或消息判死的订单会让客户端永久排队。
     */
    @Operation(summary = "轮询秒杀订单处理结果")
    @SaCheckRole(value = {UserRole.STUDENT}, mode = SaMode.OR)
    @GetMapping("/result/{requestId}")
    public Result<SeckillResultVO> querySeckillResult(@PathVariable("requestId") String requestId) {
        Long studentId = AuthUtil.requireStudentId();
        SeckillResultVO vo = studentGiftService.getSeckillResultByRequestId(requestId, studentId);
        String message;
        if (SeckillResultVO.STATUS_SUCCESS.equals(vo.getStatus())) {
            message = "订单落库成功";
        } else if (SeckillResultVO.STATUS_FAILED.equals(vo.getStatus())) {
            message = "订单处理失败";
        } else {
            message = "排队处理中";
        }
        return Result.success(message, vo);
    }
}
