package com.campus.modules.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生活动参与/报名/打卡记录实体
 * 对应需求: 3.3 活动报名 / 3.4 活动打卡 / 3.5 活动记录 / 4. 数据需求
 */
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

    /** 打卡点距活动目标中心距离(米) */
    private Integer checkinDistance;

    /** 打卡核销使用的动态二维码nonce */
    private String qrNonceUsed;

    /** 实际获得的奖励积分 */
    private Integer pointsAwarded;

    /** 参与状态: 1-已报名(待打卡) 2-已完成(打卡成功发分) 3-未完成或已失效 */
    private Integer status;

    /** 备注/打卡审核反馈 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.campus.modules.activity.enums.StudentActivityStatus getStatusEnum() {
        return com.campus.modules.activity.enums.StudentActivityStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    // 注意：不要为 status 再写一个「参数是枚举」的 setStatus 重载。
    // Jackson 把重载视为同一属性 status 的冲突 setter，一旦给其中一个加 @JsonIgnore，
    // 整个 status 属性会连同 getter 一起被丢弃——线上表现就是「我的活动」接口
    // 根本不返回 status，而移动端正是靠它判断已报名/候补中。
    // 需要枚举入参请用 setStatusEnum(...)。
    public void setStatusEnum(com.campus.modules.activity.enums.StudentActivityStatus status) {
        setStatus(status != null ? status.getCode() : null);
    }
}
