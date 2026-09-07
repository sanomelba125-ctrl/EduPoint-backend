package com.campus.modules.admin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分明细导出 DTO
 */
@Data
public class PointRecordExportDTO {

    @ExcelProperty("流水号")
    private Long id;

    @ColumnWidth(15)
    @ExcelProperty("学生学号")
    private String studentNo;

    @ColumnWidth(20)
    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("原积分余额")
    private Integer beforePoints;

    @ExcelProperty("变动积分")
    private Integer changePoints;

    @ExcelProperty("变动后余额")
    private Integer afterPoints;

    @ColumnWidth(15)
    @ExcelProperty("业务类型")
    private String bizType;

    @ColumnWidth(30)
    @ExcelProperty("关联业务单号/原因")
    private String bizRef;

    @ColumnWidth(25)
    @ExcelProperty("发生时间")
    private LocalDateTime createTime;
}
