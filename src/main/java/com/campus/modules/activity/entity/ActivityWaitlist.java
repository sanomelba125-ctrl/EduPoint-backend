package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名候补排队记录实体
 * 对应需�? 3.3 活动报名(候补机制) / 4. 数据需�? */
@Data
@TableName("activity_waitlist")
public class ActivityWaitlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** 候补学生ID */
    private Long studentId;

    /** 候补排队序号(按报名时间自增递增) */
    private Integer queueNumber;

    /** 候补状�? 1-候补�?2-已递补转正 3-主动取消 4-活动结束已失�?*/
    private Integer status;

    /** 进入候补队列时间 */
    private LocalDateTime waitTime;

    /** 递补成功转正时间 */
    private LocalDateTime promotedTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
