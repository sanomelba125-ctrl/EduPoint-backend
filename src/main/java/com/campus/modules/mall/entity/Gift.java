package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分商城礼品库实�? * 对应需�? 3.8 礼物管理 / 4. 数据需�? */
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

    /** 兑换所需积分单价(正整�? */
    private Integer pointsRequired;

    /** 礼品入库总库�?*/
    private Integer totalStock;

    /** 当前可用库存(兑换实时扣减�?=0) */
    private Integer stock;

    /** 乐观锁版本号(高并发防超卖) */
    @Version
    private Integer version;

    /** 上架状�? 0-已下�?停用 1-上架兑换�?*/
    private Integer status;

    /** 创建管理员ID */
    private Long createdBy;

    /** 修改管理员ID */
    private Long updatedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-正常 1-已删�?*/
    private Integer deleted;
}
