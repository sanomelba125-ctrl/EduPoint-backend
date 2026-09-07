package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校园活动实体
 * 对应需�? 3.2 活动管理 / 3.14 地图与定�?/ 3.16 电子证书 / 4. 数据需�? */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动标题 */
    private String title;

    /** 活动详情说明 */
    private String description;

    /** 积分与打卡规则说�?*/
    private String ruleDescription;

    /** 活动封面图URL */
    private String coverImage;

    /** 打卡完成奖励积分 */
    private Integer points;

    /** 活动开�?报名打卡起始时间 */
    private LocalDateTime startTime;

    /** 活动结束/打卡截止时间 */
    private LocalDateTime endTime;

    /** 正式报名人数上限(0表示不限制人�? */
    private Integer registrationLimit;

    /** 已成功报名人�?*/
    private Integer participantCount;

    /** 是否启用候补队列: 0-�?1-�?*/
    private Integer waitlistEnabled;

    /** 地点名称 */
    private String locationName;

    /** 详细地址说明 */
    private String locationAddress;

    /** 经度(-180~180) */
    private BigDecimal longitude;

    /** 纬度(-90~90) */
    private BigDecimal latitude;

    /** 允许打卡签到半径(米，默认300�? */
    private Integer locationRadius;

    /** 是否启用定位范围签到: 0-�?1-�?*/
    private Integer locationCheckEnabled;

    /** 是否颁发电子荣誉证书: 0-�?1-�?*/
    private Integer certificateEnabled;

    /** 证书名称 */
    private String certificateTitle;

    /** 证书签发单位 */
    private String certificateIssuer;

    /** 活动状�? 0-未开�?1-进行�?2-已截�?已结�?3-已关�?归档 */
    private Integer status;

    /** 发布管理员ID */
    private Long createdBy;

    /** 最后修改管理员ID */
    private Long updatedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-正常 1-已删�?*/
    private Integer deleted;
}
