package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 礼品更新请求入参
 *
 * 本接口只维护礼品基础信息（名称/描述/图片/所需积分/上下架状态）。
 * 库存相关列（stock 实时可用库存、totalStock 入库总量）不在此处变更：
 * 它们由兑换扣减、退单回补与补货三个动作在数据库当前值上原子维护，
 * 若允许编辑接口直接赋值，管理员改个名字就会把读出来的库存快照写回，
 * 覆盖并发兑换刚扣掉的数量。
 * 需要追加库存请调用补货接口 POST /gift/{id}/restock。
 */
@Data
public class GiftUpdateDTO {

    @NotNull(message = "礼品ID不能为空")
    private Long id;

    private String name;

    private String description;

    private String image;

    @Min(value = 1, message = "所需积分必须大于0")
    private Integer pointsRequired;

    private Integer status;

    public void setPointsCost(Integer pointsCost) {
        if (this.pointsRequired == null) {
            this.pointsRequired = pointsCost;
        }
    }

    public Integer getPointsCost() {
        return this.pointsRequired;
    }
}
