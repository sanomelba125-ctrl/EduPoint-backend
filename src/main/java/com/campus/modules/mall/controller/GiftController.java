package com.campus.modules.mall.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.common.auth.AuthUtil;
import com.campus.common.constant.UserRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
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
     * 分页查询礼品列表（管理端与移动端通用，需登录鉴权防未授权刷取与爬虫）
     * 对应 API.md 5.1 GET /gift/list
     */
    @SaCheckLogin
    @GetMapping({"/list", "/page"})
    public Result<Page<GiftVO>> getGiftList(GiftQueryDTO queryDTO) {
        Page<GiftVO> page = giftService.getGiftPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 查询单个礼品详情（需登录鉴权）
     * 对应 GET /gift/{id}
     */
    @SaCheckLogin
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
     * 管理端库存调整（改库存：支持盘盈补货与盘亏核减）
     * 对应需求 3.8 礼物管理 —— 库存维护
     * quantity > 0 为入库增加，quantity < 0 为盘亏核减（采用条件 SQL 原子更新）
     * 例：POST /api/gift/12/stock/adjust?quantity=10 或 -5
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping({"/{id}/stock/adjust", "/{id}/adjust-stock"})
    public Result<GiftVO> adjustStock(@PathVariable("id") Long id,
                                      @RequestParam("quantity") Integer quantity) {
        Long adminId = getAdminId();
        GiftVO vo = giftService.adjustStock(id, quantity, adminId);
        String action = quantity != null && quantity > 0 ? "库存入库增加" : "库存盘亏核减";
        return Result.success(action + "成功", vo);
    }

    /**
     * 管理端补货兼容接口（转发至统一库存调整接口）
     * 例：POST /api/gift/12/restock?quantity=50
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @PostMapping("/{id}/restock")
    public Result<GiftVO> restock(@PathVariable("id") Long id,
                                  @RequestParam("quantity") Integer quantity) {
        Long adminId = getAdminId();
        GiftVO vo = giftService.adjustStock(id, quantity, adminId);
        String action = quantity != null && quantity > 0 ? "补货成功" : "库存核减成功";
        return Result.success(action, vo);
    }

    /**
     * 管理端删除礼品
     * 对应 DELETE /gift/{id}
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public Result<String> deleteGift(@PathVariable Long id) {
        String msg = giftService.deleteGift(id);
        return Result.success(msg, msg);
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
