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
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.GiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 礼品管理业务实现
 */
@Service
@RequiredArgsConstructor
public class GiftServiceImpl extends ServiceImpl<GiftMapper, Gift> implements GiftService {

    private final StudentGiftMapper studentGiftMapper;

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
        gift.setStatus(createDTO.getStatus() != null ? createDTO.getStatus() : 1);
        gift.setCreatedBy(adminId);

        this.save(gift);
        return convertToVO(gift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftVO updateGift(GiftUpdateDTO updateDTO, Long adminId) {
        Gift gift = this.getById(updateDTO.getId());
        if (gift == null) {
            throw new BusinessException("要修改的礼品不存在");
        }

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
        // 注意：基础信息编辑不再更新 stock 与 totalStock，避免管理员改名/改图冲掉并发兑换扣减的实时库存
        if (updateDTO.getStatus() != null) {
            gift.setStatus(updateDTO.getStatus());
        }
        if (gift.getVersion() == null) {
            gift.setVersion(0);
        }
        gift.setUpdatedBy(adminId);

        boolean updated = this.updateById(gift);
        if (!updated) {
            // 乐观锁重试兜底
            Gift fresh = this.getById(updateDTO.getId());
            if (fresh != null) {
                if (StringUtils.hasText(updateDTO.getName())) fresh.setName(updateDTO.getName());
                if (updateDTO.getDescription() != null) fresh.setDescription(updateDTO.getDescription());
                if (StringUtils.hasText(updateDTO.getImage())) fresh.setImage(updateDTO.getImage());
                if (updateDTO.getPointsRequired() != null) fresh.setPointsRequired(updateDTO.getPointsRequired());
                if (updateDTO.getStatus() != null) fresh.setStatus(updateDTO.getStatus());
                if (fresh.getVersion() == null) fresh.setVersion(0);
                fresh.setUpdatedBy(adminId);
                this.updateById(fresh);
                return convertToVO(fresh);
            }
        }
        return convertToVO(gift);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGift(Long id) {
        Gift gift = this.getById(id);
        if (gift == null) {
            return;
        }

        // 检查是否已有学生兑换订单
        Long orderCount = studentGiftMapper.selectCount(
                new LambdaQueryWrapper<StudentGift>().eq(StudentGift::getGiftId, id)
        );

        if (orderCount != null && orderCount > 0) {
            // 已有兑换记录，严禁物理删除，降级为自动下架
            gift.setStatus(0);
            this.updateById(gift);
            throw new BusinessException("该礼品已有学生兑换记录，为保证订单可溯不可删除，已自动转为下架状态！");
        }

        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        Gift gift = this.getById(id);
        if (gift == null) {
            throw new BusinessException("礼品不存在");
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

    private GiftVO convertToVO(Gift gift) {
        GiftVO vo = new GiftVO();
        BeanUtils.copyProperties(gift, vo);
        return vo;
    }
}
