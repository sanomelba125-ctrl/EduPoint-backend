package com.campus.modules.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.mall.dto.GiftCreateDTO;
import com.campus.modules.mall.dto.GiftQueryDTO;
import com.campus.modules.mall.dto.GiftUpdateDTO;
import com.campus.modules.mall.dto.GiftVO;
import com.campus.modules.mall.entity.Gift;

/**
 * 礼品管理业务接口
 */
public interface GiftService extends IService<Gift> {

    /**
     * 分页查询礼品列表（支持关键词与上架状态过滤）
     */
    Page<GiftVO> getGiftPage(GiftQueryDTO queryDTO);

    /**
     * 查询礼品详情
     */
    GiftVO getGiftDetail(Long id);

    /**
     * 管理端创建礼品
     */
    GiftVO createGift(GiftCreateDTO createDTO, Long adminId);

    /**
     * 管理端更新礼品（仅基础信息，不含库存）
     */
    GiftVO updateGift(GiftUpdateDTO updateDTO, Long adminId);

    /**
     * 管理端补货：为指定礼品追加入库数量，入库总量与可用库存同步增加
     */
    GiftVO restock(Long id, Integer quantity, Long adminId);

    /**
     * 管理端调整库存（改库存：正数入库补货，负数盘亏核减，向加库存看齐采用条件 SQL 原子更新）
     *
     * @param id 礼品ID
     * @param quantity 调整件数（正数增加，负数减少）
     * @param adminId 操作员ID
     * @return 调整后的礼品视图对象
     */
    GiftVO adjustStock(Long id, Integer quantity, Long adminId);

    /**
     * 管理端删除礼品（防误删校验：无兑换记录则物理删除，有兑换记录则降级为下架）
     * @return 操作结果提示信息
     */
    String deleteGift(Long id);

    /**
     * 切换礼品上架/下架状态
     */
    void toggleStatus(Long id, Integer status);
}
