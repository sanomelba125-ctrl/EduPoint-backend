package com.campus.modules.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.dto.GiftCreateDTO;
import com.campus.modules.mall.dto.GiftQueryDTO;
import com.campus.modules.mall.dto.GiftUpdateDTO;
import com.campus.modules.mall.dto.GiftVO;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.enums.GiftStatus;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import com.campus.modules.mall.service.GiftService;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.GiftCacheService;

/**
 * 礼品管理业务实现
 */
@Slf4j
@Service
public class GiftServiceImpl extends ServiceImpl<GiftMapper, Gift> implements GiftService {

    /** 单次补货上限，防误操作输入天文数字 */
    private static final int MAX_RESTOCK_QUANTITY = 100000;

    private final StudentGiftMapper studentGiftMapper;
    private final GiftStockRedisService giftStockRedisService;
    private final GiftCacheService giftCacheService;

    @Autowired
    public GiftServiceImpl(StudentGiftMapper studentGiftMapper,
                           GiftStockRedisService giftStockRedisService,
                           GiftCacheService giftCacheService) {
        this.studentGiftMapper = studentGiftMapper;
        this.giftStockRedisService = giftStockRedisService;
        this.giftCacheService = giftCacheService;
    }

    public GiftServiceImpl(StudentGiftMapper studentGiftMapper) {
        this(studentGiftMapper, null, null);
    }

    @Override
    public Page<GiftVO> getGiftPage(GiftQueryDTO queryDTO) {
        Page<Gift> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());

        LambdaQueryWrapper<Gift> queryWrapper = new LambdaQueryWrapper<Gift>()
                .like(StringUtils.hasText(queryDTO.getKeyword()), Gift::getName, queryDTO.getKeyword())
                .eq(queryDTO.getStatus() != null, Gift::getStatus, queryDTO.getStatus())
                .orderByDesc(Gift::getCreateTime);

        Page<Gift> giftPage = this.page(page, queryWrapper);

        List<GiftVO> voList = giftPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<GiftVO> voPage = new Page<>(giftPage.getCurrent(), giftPage.getSize(), giftPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public GiftVO getGiftDetail(Long id) {
        Gift gift = this.getById(id);
        if (gift == null) {
            throw new BusinessException("礼品不存在或已下架");
        }
        return convertToVO(gift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftVO createGift(GiftCreateDTO createDTO, Long adminId) {
        Gift gift = new Gift();
        BeanUtils.copyProperties(createDTO, gift);
        gift.setStock(createDTO.getTotalStock());
        gift.setVersion(0);
        gift.setStatus(createDTO.getStatus() != null ? createDTO.getStatus() : GiftStatus.ON_SHELF.getCode());
        gift.setCreatedBy(adminId);

        this.save(gift);

        if (giftStockRedisService != null) {
            giftStockRedisService.warmupStock(gift.getId(), gift.getStock() != null ? gift.getStock() : 0);
        }
        if (giftCacheService != null) {
            giftCacheService.addGiftToBloom(gift.getId());
        }

        return convertToVO(gift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftVO updateGift(GiftUpdateDTO updateDTO, Long adminId) {
        Gift gift = this.getById(updateDTO.getId());
        if (gift == null) {
            throw new BusinessException("要修改的礼品不存在");
        }

        applyUpdateDto(gift, updateDTO, adminId);

        boolean updated = this.updateById(gift);
        GiftVO resultVO;
        if (!updated) {
            // 乐观锁重试兜底
            Gift fresh = this.getById(updateDTO.getId());
            if (fresh != null) {
                applyUpdateDto(fresh, updateDTO, adminId);
                this.updateById(fresh);
                resultVO = convertToVO(fresh);
            } else {
                resultVO = convertToVO(gift);
            }
        } else {
            resultVO = convertToVO(gift);
        }

        if (giftStockRedisService != null) {
            giftStockRedisService.evictGiftCacheWithDoubleDelete(updateDTO.getId());
        }

        return resultVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftVO restock(Long id, Integer quantity, Long adminId) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("补货数量必须为正整数");
        }
        return adjustStock(id, quantity, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftVO adjustStock(Long id, Integer quantity, Long adminId) {
        if (quantity == null || quantity == 0) {
            throw new BusinessException("库存调整数量不能为 0");
        }
        int absQuantity = Math.abs(quantity);
        if (absQuantity > MAX_RESTOCK_QUANTITY) {
            throw new BusinessException("单次库存调整数量不能超过 " + MAX_RESTOCK_QUANTITY + " 件，请分批操作");
        }

        Gift gift = this.getById(id);
        if (gift == null) {
            throw new BusinessException("目标礼品不存在或已被删除");
        }
        int beforeStock = gift.getStock() != null ? gift.getStock() : 0;
        int beforeTotal = gift.getTotalStock() != null ? gift.getTotalStock() : 0;

        int rows;
        if (quantity > 0) {
            // 1. 入库补货（盘盈）：可用库存与总入库同步原子自增
            rows = baseMapper.restock(id, quantity, adminId);
            if (rows == 0) {
                throw new BusinessException("补货失败，礼品可能已被删除，请刷新列表后重试");
            }
            log.info("礼品入库补货成功: giftId={}, 名称={}, 本次入库=+{}件, 变动前可用库存={}, 变动前入库总量={}, 操作管理员ID={}",
                    id, gift.getName(), quantity, beforeStock, beforeTotal, adminId);
        } else {
            // 2. 盘亏核减（报损）：向加库存看齐采用条件 SQL 原子自减，WHERE stock >= #{quantity} 严格杜绝负库存
            rows = baseMapper.deductStock(id, absQuantity, adminId);
            if (rows == 0) {
                throw new BusinessException("库存核减失败：当前可用库存（" + beforeStock + "件）不足以核减 " + absQuantity + " 件，或礼品已被删除！");
            }
            log.info("礼品盘亏核减成功: giftId={}, 名称={}, 本次核减=-{}件, 变动前可用库存={}, 变动前入库总量={}, 操作管理员ID={}",
                    id, gift.getName(), absQuantity, beforeStock, beforeTotal, adminId);
        }

        Gift fresh = this.getById(id);
        if (giftStockRedisService != null && fresh != null) {
            giftStockRedisService.evictGiftCacheWithDoubleDelete(id);
            giftStockRedisService.warmupStock(id, fresh.getStock() != null ? fresh.getStock() : 0);
        }

        return convertToVO(fresh != null ? fresh : gift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteGift(Long id) {
        Gift gift = this.getById(id);
        if (gift == null) {
            return "礼品不存在";
        }

        if (giftStockRedisService != null) {
            giftStockRedisService.evictGiftCacheWithDoubleDelete(id);
        }

        // 检查是否已有学生兑换订单
        Long orderCount = studentGiftMapper.selectCount(
                new LambdaQueryWrapper<StudentGift>().eq(StudentGift::getGiftId, id)
        );

        if (orderCount != null && orderCount > 0) {
            // 已有兑换记录，严禁物理删除，降级为自动下架（正常提交事务，严禁抛出异常以避免触发事务回滚）
            gift.setStatus(GiftStatus.OFF_SHELF.getCode());
            if (gift.getVersion() == null) {
                gift.setVersion(0);
            }
            boolean updated = this.updateById(gift);
            if (!updated) {
                // 兜底强制更新货架状态（绕过乐观锁版本检查，确保管理员意志落地）
                this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Gift>()
                        .set(Gift::getStatus, GiftStatus.OFF_SHELF.getCode())
                        .eq(Gift::getId, id));
            }
            log.warn("礼品存在历史兑换订单，不可物理删除，已自动降级为下架状态: giftId={}, orderCount={}", id, orderCount);
            return "该礼品已有学生兑换记录，为保证订单可溯不可删除，已自动转为下架状态！";
        }

        this.removeById(id);
        log.info("礼品无兑换记录，已成功物理删除: giftId={}", id);
        return "删除成功";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        Gift gift = this.getById(id);
        if (gift == null) {
            throw new BusinessException("礼品不存在");
        }
        if (giftStockRedisService != null) {
            giftStockRedisService.evictGiftCacheWithDoubleDelete(id);
        }
        gift.setStatus(status);
        if (gift.getVersion() == null) {
            gift.setVersion(0);
        }
        boolean updated = this.updateById(gift);
        if (!updated) {
            // 兜底强制更新货架状态
            this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Gift>()
                    .set(Gift::getStatus, status)
                    .eq(Gift::getId, id));
        }
    }

    private void applyUpdateDto(Gift gift, GiftUpdateDTO updateDTO, Long adminId) {
        if (StringUtils.hasText(updateDTO.getName())) {
            gift.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            gift.setDescription(updateDTO.getDescription());
        }
        if (StringUtils.hasText(updateDTO.getImage())) {
            gift.setImage(updateDTO.getImage());
        }
        if (updateDTO.getPointsRequired() != null) {
            gift.setPointsRequired(updateDTO.getPointsRequired());
        }
        // 注意：Gift 实体的 stock / totalStock 标注了 updateStrategy = FieldStrategy.NEVER，
        // 这里 updateById 生成的 SQL 不会带这两列，所以改名称/图片/积分不会冲掉并发兑换已扣减的实时库存。
        // 库存变更只走 GiftMapper 上的原子 SQL（补货 restock / 兑换 reduceStockWithOptimisticLock / 退单 increaseStock）。
        if (updateDTO.getStatus() != null) {
            gift.setStatus(updateDTO.getStatus());
        }
        if (gift.getVersion() == null) {
            gift.setVersion(0);
        }
        gift.setUpdatedBy(adminId);
    }

    private GiftVO convertToVO(Gift gift) {
        GiftVO vo = new GiftVO();
        BeanUtils.copyProperties(gift, vo);
        return vo;
    }
}
