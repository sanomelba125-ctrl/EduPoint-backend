package com.campus.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.alibaba.excel.EasyExcel;
import com.campus.modules.admin.dto.ActivityExportDTO;
import com.campus.modules.admin.dto.PointRecordExportDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台数据看板 - 报表导出控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
public class ExportController {

    // 实际业务中应该注入对应的 Service
    // private final ActivityService activityService;
    // private final StudentPointRecordService pointRecordService;

    /**
     * 导出活动明细
     */
    @SaCheckLogin
    @GetMapping("/activities")
    public void exportActivities(HttpServletResponse response) throws IOException {
        try {
            setExcelResponseProp(response, "活动明细报表");
            
            // TODO: 替换为真实的从 Service 查询的数据
            List<ActivityExportDTO> list = new ArrayList<>();
            // list = activityService.getExportData();
            
            EasyExcel.write(response.getOutputStream(), ActivityExportDTO.class)
                    .sheet("活动列表")
                    .doWrite(list);
        } catch (Exception e) {
            log.error("导出活动明细失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500, \"message\":\"导出失败\"}");
        }
    }

    /**
     * 导出积分明细
     */
    @SaCheckLogin
    @GetMapping("/points")
    public void exportPoints(HttpServletResponse response) throws IOException {
        try {
            setExcelResponseProp(response, "学生积分明细报表");

            // TODO: 替换为真实的从 Service 查询的数据
            List<PointRecordExportDTO> list = new ArrayList<>();
            // list = pointRecordService.getExportData();

            EasyExcel.write(response.getOutputStream(), PointRecordExportDTO.class)
                    .sheet("积分流水")
                    .doWrite(list);
        } catch (Exception e) {
            log.error("导出积分明细失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500, \"message\":\"导出失败\"}");
        }
    }

    /**
     * 设置响应头
     */
    private void setExcelResponseProp(HttpServletResponse response, String rawFileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
    }
}
