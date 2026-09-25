package com.campus.modules.mall.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.service.StudentGiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 礼品兑换订单控制器
 * 包含学生端兑换、查询本人订单、自主取消，以及管理端订单查询、核销提货码、后台退单
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StudentGiftController {

    private final StudentGiftService studentGiftService;

    // ==========================================
    // 移动学生端接口 (Student Endpoints)
    // ==========================================

    /**
     * 学生进入礼品详情页时获取防重兑换令牌 (P2-2 治理升级)
     * 对应 GET /gift/{id}/token, /student/gift/{id}/token, /mall/gift/{id}/token
     */
    @GetMapping({"/gift/{id}/token", "/student/gift/{id}/token", "/mall/gift/{id}/token"})
    public Result<GiftExchangeTokenVO> getExchangeToken(@PathVariable("id") Long id) {
        Long studentId = getStudentId();
        GiftExchangeTokenVO vo = studentGiftService.getExchangeToken(id, studentId);
        return Result.success("防重令牌获取成功", vo);
    }

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
     * 学生获取礼品专属动态防伪提货二维码
     * 对应 GET /gift/orders/{id}/pickup-qrcode, /student/gift/orders/{id}/pickup-qrcode, /mall/gift/orders/{id}/pickup-qrcode
     */
    @GetMapping({"/gift/orders/{id}/pickup-qrcode", "/student/gift/orders/{id}/pickup-qrcode", "/mall/gift/orders/{id}/pickup-qrcode"})
    public Result<GiftPickupQrCodeVO> getPickupQrCode(@PathVariable("id") Long id) {
        Long studentId = getStudentId();
        GiftPickupQrCodeVO vo = studentGiftService.getPickupQrCode(id, studentId);
        return Result.success("动态提货二维码生成成功", vo);
    }

    // ==========================================
    // 管理端接口 (Admin Endpoints)
    // ==========================================

    /**
     * 管理端全量查询兑换订单列表
     * 对应 GET /gift/order/list 或 /gift/orders
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @GetMapping({"/gift/order/list", "/gift/orders", "/admin/gift/orders"})
    public Result<Page<StudentGiftVO>> getAdminOrderList(GiftOrderQueryDTO queryDTO) {
        Page<StudentGiftVO> page = studentGiftService.getAdminOrderPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 管理端查询礼品兑换订单全局指标统计（避免分页当前页数据当成全局数据）
     * 对应 GET /gift/order/metrics 或 /admin/gift/orders/metrics
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @GetMapping({"/gift/order/metrics", "/admin/gift/orders/metrics", "/admin/gift/order/metrics"})
    public Result<GiftOrderMetricsVO> getAdminOrderMetrics() {
        GiftOrderMetricsVO metrics = studentGiftService.getOrderMetrics();
        return Result.success(metrics);
    }

    /**
     * 管理端核销提货码并确认发放（人工手动输入 6~8 位数字提货码兜底）
     * 对应 API.md 5.5 POST /gift/deliver 或 /gift/order/confirm-delivery
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping({"/gift/deliver", "/gift/order/confirm-delivery", "/gift/order/deliver"})
    public Result<Void> confirmDelivery(@Valid @RequestBody DeliveryConfirmDTO dto) {
        Long adminId = getAdminId();
        studentGiftService.confirmDelivery(dto, adminId);
        return Result.success("提货码核销成功，已确认发放", null);
    }

    /**
     * 管理端/扫码枪秒级扫码核销动态提货二维码
     * 对应 POST /gift/order/verify-pickup-qrcode, /gift/verify-pickup-qrcode, /mall/gift/verify-pickup-qrcode, /admin/gift/order/verify-pickup-qrcode
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping({"/gift/order/verify-pickup-qrcode", "/gift/verify-pickup-qrcode", "/mall/gift/verify-pickup-qrcode", "/admin/gift/order/verify-pickup-qrcode"})
    public Result<PickupVerifyResultVO> verifyPickupQrCode(@Valid @RequestBody PickupQrVerifyDTO dto) {
        Long adminId = getAdminId();
        PickupVerifyResultVO vo = studentGiftService.verifyAndDeliverByQrCode(dto, adminId);
        return Result.success("扫码核销成功，已确认发放", vo);
    }

    /**
     * 管理端取消订单并退款
     * 对应 POST /gift/order/cancel 或 /admin/gift/order/cancel
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
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
