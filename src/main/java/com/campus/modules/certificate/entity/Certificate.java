package com.campus.modules.certificate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
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

    public com.campus.modules.certificate.enums.CertificateStatus getStatusEnum() {
        return com.campus.modules.certificate.enums.CertificateStatus.of(this.status);
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public void setStatus(com.campus.modules.certificate.enums.CertificateStatus status) {
        this.status = status != null ? status.getCode() : null;
    }
}
