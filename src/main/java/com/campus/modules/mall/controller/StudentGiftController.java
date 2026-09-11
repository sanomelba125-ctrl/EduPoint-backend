package com.campus.modules.mall.controller;

import com.campus.common.auth.AuthUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.service.StudentGiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 礼品兑换订单控制器
 * 包含学生端兑换、查询本人订单、自主取消，以及管理端订单查询、核销提货码、后台退单
 */
@RestController
@RequiredArgsConstructor
public class StudentGiftController {

    private final StudentGiftService studentGiftService;

    // ==========================================
    // 学生端接口 (Student Endpoints)
    // ==========================================

    /**
     * 学生发起礼品兑换
     * 对应 API.md 5.3 POST /gift/exchange 与 /student/gift/exchange
     */
    @PostMapping({"/gift/exchange", "/student/gift/exchange"})
    public Result<GiftExchangeVO> exchangeGift(@Valid @RequestBody GiftExchangeDTO dto) {
        Long studentId = getStudentId();
        GiftExchangeVO vo = studentGiftService.exchangeGift(dto, studentId);
        return Result.success("兑换成功，提货码已生成", vo);
    }

    /**
     * 学生查询本人兑换订单记录
     * 对应 API.md 5.4 GET /gift/my 与 /student/gift/my-orders
     */
    @GetMapping({"/gift/my", "/student/gift/my", "/student/gift/my-orders"})
    public Result<Page<StudentGiftVO>> getMyOrders(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "status", required = false) Integer status) {
        Long studentId = getStudentId();
        Page<StudentGiftVO> result = studentGiftService.getMyOrders(page, size, status, studentId);
        return Result.success(result);
    }

    /**
     * 学生自主取消待提货订单
     * 对应 POST /gift/orders/{id}/cancel 或 /student/gift/orders/{id}/cancel
     */
    @PostMapping({"/gift/orders/{id}/cancel", "/student/gift/orders/{id}/cancel"})
    public Result<Void> studentCancelOrder(@PathVariable("id") Long id) {
        Long studentId = getStudentId();
        studentGiftService.studentCancelOrder(id, studentId);
        return Result.success("订单取消成功，积分已原路返还", null);
    }

    /**
     * 取消礼品兑换订单并返还积分（兼容移动端自主取消与管理端取消调用）
     * 对应 POST /gift/cancel
     */
    @PostMapping({"/gift/cancel", "/student/gift/cancel"})
    public Result<Void> cancelOrder(@RequestBody Map<String, Object> params) {
        Object orderIdObj = params.get("orderId");
        if (orderIdObj == null) {
            orderIdObj = params.get("id");
        }
        if (orderIdObj == null) {
            throw new BusinessException("缺少 orderId 参数");
        }
        Long orderId = Long.valueOf(orderIdObj.toString());

        // 判断当前是学生还是管理员操作
        if (AuthUtil.isStudent()) {
            Long studentId = AuthUtil.requireStudentId();
            studentGiftService.studentCancelOrder(orderId, studentId);
            return Result.success("订单已取消，积分已原路退回", null);
        } else if (AuthUtil.isAdmin()) {
            Long adminId = AuthUtil.requireAdminId();
            OrderCancelDTO cancelDTO = new OrderCancelDTO();
            cancelDTO.setOrderId(orderId);
            cancelDTO.setCancelReason(params.get("reason") != null ? params.get("reason").toString() : "管理端取消");
            cancelDTO.setRefundPoints(true);
            studentGiftService.adminCancelOrder(cancelDTO, adminId);
            return Result.success("管理端取消成功", null);
        } else {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "未授权的角色身份");
        }
    }

    // ==========================================
    // 管理端接口 (Admin Endpoints)
    // ==========================================

    /**
     * 管理端全量查询兑换订单列表
     * 对应 GET /gift/order/list 或 /gift/orders
     */
    @GetMapping({"/gift/order/list", "/gift/orders", "/admin/gift/orders"})
    public Result<Page<StudentGiftVO>> getAdminOrderList(GiftOrderQueryDTO queryDTO) {
        getAdminId();
        Page<StudentGiftVO> page = studentGiftService.getAdminOrderPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 管理端核销提货码并确认发放
     * 对应 API.md 5.5 POST /gift/deliver 或 /gift/order/confirm-delivery
     */
    @PostMapping({"/gift/deliver", "/gift/order/confirm-delivery", "/gift/order/deliver"})
    public Result<Void> confirmDelivery(@Valid @RequestBody DeliveryConfirmDTO dto) {
        Long adminId = getAdminId();
        studentGiftService.confirmDelivery(dto, adminId);
        return Result.success("提货码核销成功，已确认发放", null);
    }

    /**
     * 管理端取消订单并退款
     * 对应 POST /gift/order/cancel 或 /admin/gift/order/cancel
     */
    @PostMapping({"/gift/order/cancel", "/admin/gift/order/cancel"})
    public Result<Void> adminCancelOrder(@Valid @RequestBody OrderCancelDTO dto) {
        Long adminId = getAdminId();
        studentGiftService.adminCancelOrder(dto, adminId);
        return Result.success("订单已取消并完成积分退还", null);
    }

    // ==========================================
    // 鉴权辅助方法
    // ==========================================

    private Long getStudentId() {
        return AuthUtil.requireStudentId();
    }

    private Long getAdminId() {
        return AuthUtil.requireAdminId();
    }
}
