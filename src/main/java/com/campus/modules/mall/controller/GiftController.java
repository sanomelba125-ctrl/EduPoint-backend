package com.campus.modules.mall.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.mall.dto.GiftCreateDTO;
import com.campus.modules.mall.dto.GiftQueryDTO;
import com.campus.modules.mall.dto.GiftUpdateDTO;
import com.campus.modules.mall.dto.GiftVO;
import com.campus.modules.mall.service.GiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 积分商城礼品控制器
 * 支持 /gift 与 /mall/gift，全局路径 /api/gift
 */
@RestController
@RequestMapping({"/gift", "/mall/gift"})
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    /**
     * 分页查询礼品列表（管理端与移动端通用）
     * 对应 API.md 5.1 GET /gift/list
     */
    @GetMapping({"/list", "/page"})
    public Result<Page<GiftVO>> getGiftList(GiftQueryDTO queryDTO) {
        Page<GiftVO> page = giftService.getGiftPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 查询单个礼品详情
     * 对应 GET /gift/{id}
     */
    @GetMapping("/{id}")
    public Result<GiftVO> getGiftDetail(@PathVariable("id") Long id) {
        GiftVO vo = giftService.getGiftDetail(id);
        return Result.success(vo);
    }

    /**
     * 管理端新增礼品
     * 对应 API.md 5.2 POST /gift/create
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping({"/create", "/save"})
    public Result<GiftVO> createGift(@Valid @RequestBody GiftCreateDTO createDTO) {
        Long adminId = getAdminId();
        GiftVO vo = giftService.createGift(createDTO, adminId);
        return Result.success("礼品上架成功", vo);
    }

    /**
     * 管理端修改礼品
     * 对应 PUT /gift/update
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PutMapping({"/update", "/edit"})
    public Result<GiftVO> updateGift(@Valid @RequestBody GiftUpdateDTO updateDTO) {
        Long adminId = getAdminId();
        GiftVO vo = giftService.updateGift(updateDTO, adminId);
        return Result.success("修改礼品成功", vo);
    }

    /**
     * 管理端删除礼品
     * 对应 DELETE /gift/{id}
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public Result<Void> deleteGift(@PathVariable Long id) {
        giftService.deleteGift(id);
        return Result.success("删除成功", null);
    }

    /**
     * 管理端切换上架状态
     * 对应 PUT /gift/{id}/status/{status} 或 PUT /gift/{id}/status?status=0
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @RequestMapping(value = {"/{id}/status/{status}", "/{id}/status"}, method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<Void> toggleStatus(
            @PathVariable("id") Long id,
            @PathVariable(value = "status", required = false) Integer pathStatus,
            @RequestParam(value = "status", required = false) Integer paramStatus) {
        Integer status = pathStatus != null ? pathStatus : paramStatus;
        if (status == null) {
            throw new BusinessException("目标货架状态不能为空");
        }
        giftService.toggleStatus(id, status);
        String action = status == 1 ? "上架" : "下架";
        return Result.success("礼品已成功" + action, null);
    }

    private Long getAdminId() {
        return AuthUtil.requireAdminId();
    }
}
