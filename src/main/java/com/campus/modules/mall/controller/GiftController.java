package com.campus.modules.mall.controller;

import cn.dev33.satoken.stp.StpUtil;
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
    @DeleteMapping("/{id}")
    public Result<Void> deleteGift(@PathVariable("id") Long id) {
        getAdminId();
        giftService.deleteGift(id);
        return Result.success("删除成功", null);
    }

    /**
     * 管理端切换上架状态
     * 对应 PUT /gift/{id}/status/{status}
     */
    @PutMapping("/{id}/status/{status}")
    public Result<Void> toggleStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        getAdminId();
        giftService.toggleStatus(id, status);
        return Result.success("状态更新成功", null);
    }

    private Long getAdminId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!loginId.startsWith("admin:")) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权访问管理端接口");
        }
        return Long.parseLong(loginId.substring("admin:".length()));
    }
}
