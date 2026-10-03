package com.campus.modules.admin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动明细导出 DTO
 */
@Data
public class ActivityExportDTO {

    @ExcelProperty("活动编号")
    private Long id;

    @ColumnWidth(25)
    @ExcelProperty("活动名称")
    private String title;

    @ColumnWidth(20)
    @ExcelProperty("活动地点")
    private String location;

    @ExcelProperty("奖励积分")
    private Integer rewardPoints;

    @ColumnWidth(20)
    @ExcelProperty("报名人数")
    private Integer enrolledCount;

    @ColumnWidth(20)
    @ExcelProperty("打卡人数")
    private Integer checkinCount;

    @ColumnWidth(25)
    @ExcelProperty("开始时间")
    private LocalDateTime startTime;

    @ColumnWidth(25)
    @ExcelProperty("结束时间")
    private LocalDateTime endTime;

    @ExcelProperty("状态(0未开始,1进行中,2已结束)")
    private Integer status;
}
