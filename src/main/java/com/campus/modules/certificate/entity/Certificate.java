package com.campus.modules.certificate.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子荣誉证书及真伪验证实体
 * 对应需求: 3.16 电子证书与验真 / 4. 数据需求
 */
@Data
@TableName("certificate")
public class Certificate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一证书编号(不可预测防伪码) */
    private String certificateNo;

    /** 获奖学生ID */
    private Long studentId;

    /** 对应活动ID */
    private Long activityId;

    /** 证书名称 */
    private String title;

    /** 证书签发单位 */
    private String issuer;

    /** 证书签发时间 */
    private LocalDateTime issueTime;

    /** 证书渲染生成的图片或PDF文件地址 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fileUrl;

    /** 证书状态: 1-有效 2-已撤销 3-已过期 */
    private Integer status;

    /** 撤销原因说明 */
    private String revokeReason;

    /** 撤销操作管理员ID */
    private Long revokedBy;

    /** 撤销时间 */
    private LocalDateTime revokedTime;

    /** 被扫码验真核验次数 */
    private Integer verifyCount;

    /** 生成时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.campus.modules.certificate.enums.CertificateStatus getStatusEnum() {
        return com.campus.modules.certificate.enums.CertificateStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    // 注意：不要为 status 再写一个「参数是枚举」的 setStatus 重载。
    // Jackson 把重载视为同一属性 status 的冲突 setter，一旦给其中一个加 @JsonIgnore，
    // 整个 status 属性会连同 getter 一起被丢弃——接口会直接少字段。
    // 需要枚举入参请用 setStatusEnum(...)。
    public void setStatusEnum(com.campus.modules.certificate.enums.CertificateStatus status) {
        setStatus(status != null ? status.getCode() : null);
    }
}
