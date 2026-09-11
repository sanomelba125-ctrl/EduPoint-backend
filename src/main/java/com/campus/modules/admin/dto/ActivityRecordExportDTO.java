package com.campus.modules.admin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生活动打卡/报名明细导出 DTO
 */
@Data
public class ActivityRecordExportDTO {

    @ExcelProperty("记录ID")
    private Long id;

    @ColumnWidth(18)
    @ExcelProperty("学号")
    private String studentNo;

    @ColumnWidth(16)
    @ExcelProperty("学生姓名")
    private String studentName;

    @ColumnWidth(28)
    @ExcelProperty("参与活动")
    private String activityTitle;

    @ColumnWidth(18)
    @ExcelProperty("参与状态")
    private String statusDesc;

    @ExcelProperty("获得积分")
    private Integer pointsAwarded;

    @ColumnWidth(22)
    @ExcelProperty("报名时间")
    private LocalDateTime signupTime;

    @ColumnWidth(22)
    @ExcelProperty("打卡时间")
    private LocalDateTime checkinTime;

    @ColumnWidth(16)
    @ExcelProperty("打卡距离(米)")
    private String checkinDistance;

    @ColumnWidth(30)
    @ExcelProperty("备注说明")
    private String remark;
}
