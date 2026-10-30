package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动报名候补排队记录实体
 * 对应需求: 3.3 活动报名(候补机制) / 4. 数据需求
 */
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

    /** 候补状态: 1-候补中 2-已递补转正 3-主动取消 4-活动结束已失效 */
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

    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.campus.modules.activity.enums.ActivityWaitlistStatus getStatusEnum() {
        return com.campus.modules.activity.enums.ActivityWaitlistStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    // 注意：不要为 status 再写一个「参数是枚举」的 setStatus 重载。
    // Jackson 把重载视为同一属性 status 的冲突 setter，一旦给其中一个加 @JsonIgnore，
    // 整个 status 属性会连同 getter 一起被丢弃——线上表现就是「我的活动」接口
    // 根本不返回 status，而移动端正是靠它判断已报名/候补中。
    // 需要枚举入参请用 setStatusEnum(...)。
    public void setStatusEnum(com.campus.modules.activity.enums.ActivityWaitlistStatus status) {
        setStatus(status != null ? status.getCode() : null);
    }
}
