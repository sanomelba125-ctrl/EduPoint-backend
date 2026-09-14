package com.campus.modules.student.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.student.dto.*;
import com.campus.modules.student.service.StudentScoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 积分台账与学分光荣榜控制器
 * 包含移动学生端双积分资产查询、流水明细、光荣榜，以及管理端对账台账、指标与人工调账
 */
@RestController
@RequiredArgsConstructor
public class StudentScoreController {

    private final StudentScoreService studentScoreService;

    // ==========================================
    // 移动学生端接口 (Student Endpoints)
    // ==========================================

    /**
     * 查询学生当前可用积分与历史累计总积分（双积分模型）
     * 对应 API.md 4.1 GET /student/score/balance
     */
    @GetMapping({"/student/score/balance", "/student/points/balance", "/score/balance"})
    public Result<StudentScoreBalanceVO> getBalance() {
        Long studentId = getStudentId();
        StudentScoreBalanceVO balanceVO = studentScoreService.getBalance(studentId);
        return Result.success(balanceVO);
    }

    /**
     * 学生查询个人积分明细台账列表
     * 对应 API.md 4.2 GET /student/score/records
     */
    @GetMapping({"/student/score/records", "/student/points/records", "/score/records"})
    public Result<Page<StudentPointRecordVO>> getMyPointRecords(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "type", required = false) Integer type) {
        Long studentId = getStudentId();
        Page<StudentPointRecordVO> pageResult = studentScoreService.getMyPointRecords(page, size, type, studentId);
        return Result.success(pageResult);
    }

    /**
     * 积分学分光荣榜查询 (全校或分学院)
     * 对应 API.md 4.3 GET /student/score/rank
     */
    @GetMapping({"/student/score/rank", "/student/rank", "/rank/list"})
    public Result<StudentRankResultVO> getRankList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @RequestParam(value = "college", required = false) String college) {
        Long currentStudentId = AuthUtil.currentStudentIdOrNull();
        StudentRankResultVO result = studentScoreService.getLeaderboard(page, size, college, currentStudentId);
        return Result.success(result);
    }

    // ==========================================
    // 管理端对账与调账接口 (Admin Endpoints)
    // ==========================================

    /**
     * 管理端全量积分对账流水明细检索
     * 对应 GET /point/record/list 与 /admin/point/record/list
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @GetMapping({"/point/record/list", "/admin/point/record/list", "/admin/point-record/list"})
    public Result<Page<StudentPointRecordVO>> getAdminPointRecords(AdminPointRecordQueryDTO queryDTO) {
        Page<StudentPointRecordVO> page = studentScoreService.getAdminRecordPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 管理端对账流水大盘指标统计
     * 对应 GET /point/record/metrics 与 /admin/point/record/metrics
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @GetMapping({"/point/record/metrics", "/admin/point/record/metrics"})
    public Result<LedgerMetricsVO> getLedgerMetrics() {
        LedgerMetricsVO metrics = studentScoreService.getLedgerMetrics();
        return Result.success(metrics);
    }

    /**
     * 管理端人工特殊积分调账（仅超管与普通管理员专属）
     * 对应 API.md 4.4 POST /student/score/adjust 与 /admin/point/adjust
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping({"/student/score/adjust", "/admin/point/adjust", "/admin/student/score/adjust"})
    public Result<Void> adjustPoints(@Valid @RequestBody PointAdjustDTO dto) {
        Long adminId = getAdminId();
        studentScoreService.adjustPoints(dto, adminId);
        return Result.success("积分调账执行成功，流水已入账", null);
    }

    // ==========================================
    // 权限校验辅助
    // ==========================================

    private Long getStudentId() {
        return AuthUtil.requireStudentId();
    }

    private Long getAdminId() {
        return AuthUtil.requireAdminId();
    }
}
