package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动动态打卡二维码核销防伪实体
 * 对应需�? 3.4 活动打卡(防作弊动态二维码) / 4. 数据需�? */
@Data
@TableName("activity_checkin_code")
public class ActivityCheckinCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** 防伪随机�?全局唯一不可重放) */
    private String nonce;

    /** 服务端签名参�?*/
    private String sign;

    /** 二维码有效截止时�?*/
    private LocalDateTime expireTime;

    /** 使用状�? 0-有效待核销 1-已被使用打卡 2-已失�?*/
    private Integer status;

    /** 使用扫码打卡的学生ID */
    private Long usedByStudentId;

    /** 扫码核销时间 */
    private LocalDateTime usedTime;

    /** 生成时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
