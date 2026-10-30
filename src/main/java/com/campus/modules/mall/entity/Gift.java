package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分商城礼品库实体
 * 对应需求: 3.8 礼物管理 / 4. 数据需求
 */
@Data
@TableName("gift")
public class Gift {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 礼品名称 */
    private String name;

    /** 礼品规格/详细描述 */
    private String description;

    /** 礼品展示主图URL */
    private String image;

    /** 兑换所需积分单价(正整数) */
    private Integer pointsRequired;

    /**
     * 礼品入库总库存。
     * updateStrategy = NEVER：实体 updateById 生成的 SQL 永不包含本列。
     * 库存的增减只允许走 gift 表上的原子 SQL（兑换扣减 / 退单回补 / 补货自增），
     * 避免管理员编辑礼品基础信息时把读出来的快照值写回，覆盖并发兑换已扣减的实时库存。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer totalStock;

    /**
     * 当前可用库存(兑换实时扣减, >=0)。
     * 同上，禁止通过实体更新回写，只由原子 SQL 维护。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer stock;

    /** 乐观锁版本号(高并发防超卖) */
    @Version
    private Integer version;

    /** 上架状态: 0-已下架停用 1-上架兑换中 */
    private Integer status;

    /** 创建管理员ID */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long createdBy;

    /** 修改管理员ID */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 创建时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-正常 1-已删除 */
    private Integer deleted;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.campus.modules.mall.enums.GiftStatus getStatusEnum() {
        return com.campus.modules.mall.enums.GiftStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    // 注意：不要为 status 再写一个「参数是枚举」的 setStatus 重载。
    // Jackson 把重载视为同一属性 status 的冲突 setter，一旦给其中一个加 @JsonIgnore，
    // 整个 status 属性会连同 getter 一起被丢弃——接口会直接少字段。
    // 需要枚举入参请用 setStatusEnum(...)。
    public void setStatusEnum(com.campus.modules.mall.enums.GiftStatus status) {
        setStatus(status != null ? status.getCode() : null);
    }
}
