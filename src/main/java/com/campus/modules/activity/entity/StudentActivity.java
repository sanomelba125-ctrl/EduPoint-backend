package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生活动参与/报名/打卡记录实体
 * 对应需�? 3.3 活动报名 / 3.4 活动打卡 / 3.5 活动记录 / 4. 数据需�? */
@Data
@TableName("student_activity")
public class StudentActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 活动ID */
    private Long activityId;

    /** 报名成功时间 */
    private LocalDateTime signupTime;

    /** 实际打卡完成时间 */
    private LocalDateTime checkinTime;

    /** 学生实际打卡位置纬度 */
    private BigDecimal checkinLatitude;

    /** 学生实际打卡位置经度 */
    private BigDecimal checkinLongitude;

    /** 打卡点距活动目标中心距离(�? */
    private Integer checkinDistance;

    /** 打卡核销使用的动态二维码nonce */
    private String qrNonceUsed;

    /** 实际获得的奖励积�?*/
    private Integer pointsAwarded;

    /** 参与状�? 1-已报�?待打�? 2-已完�?打卡成功发分) 3-未完成或已失�?*/
    private Integer status;

    /** 备注/打卡审核反馈 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
