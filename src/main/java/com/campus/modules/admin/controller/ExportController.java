package com.campus.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.activity.service.ActivityService;
import com.campus.modules.admin.dto.ActivityExportDTO;
import com.campus.modules.admin.dto.ActivityRecordExportDTO;
import com.campus.modules.admin.dto.PointRecordExportDTO;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.common.auth.AuthUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台数据看板 - 报表导出控制器
 * 支持 /admin/export 与 /dashboard/export 双路径访问，契合 API.md 规范
 */
@Slf4j
@RestController
@RequestMapping({"/admin/export", "/dashboard/export"})
@RequiredArgsConstructor
public class ExportController {

    private final ActivityService activityService;
    private final StudentActivityMapper studentActivityMapper;
    private final StudentPointRecordMapper pointRecordMapper;
    private final StudentMapper studentMapper;

    /**
     * 导出活动明细
     */
    @SaCheckLogin
    @GetMapping("/activities")
    public void exportActivities(HttpServletResponse response) throws IOException {
        AuthUtil.requireAdminId();
        try {
            setExcelResponseProp(response, "活动明细报表");

            List<Activity> activities = activityService.lambdaQuery()
                    .eq(Activity::getDeleted, 0)
                    .orderByDesc(Activity::getStartTime)
                    .list();

            // 一次性查出全量活动打卡与报名聚合统计，避免在循环中产生 2N 次数据库往返
            Map<Long, com.campus.modules.activity.dto.ActivityCountRow> countMap = studentActivityMapper.countByActivity()
                    .stream().collect(Collectors.toMap(com.campus.modules.activity.dto.ActivityCountRow::getActivityId, r -> r, (k1, k2) -> k1));

            List<ActivityExportDTO> list = new ArrayList<>();
            for (Activity act : activities) {
                ActivityExportDTO dto = new ActivityExportDTO();
                dto.setId(act.getId());
                dto.setTitle(act.getTitle());
                String loc = StringUtils.hasText(act.getLocationName()) ? act.getLocationName()
                        : (StringUtils.hasText(act.getLocationAddress()) ? act.getLocationAddress() : "校内指定区域");
                dto.setLocation(loc);
                dto.setRewardPoints(act.getPoints() != null ? act.getPoints() : 0);

                com.campus.modules.activity.dto.ActivityCountRow countRow = countMap.get(act.getId());
                dto.setEnrolledCount(countRow != null && countRow.getEnrolledCount() != null ? countRow.getEnrolledCount() : 0);
                dto.setCheckinCount(countRow != null && countRow.getCheckinCount() != null ? countRow.getCheckinCount() : 0);
                dto.setStartTime(act.getStartTime());
                dto.setEndTime(act.getEndTime());
                dto.setStatus(act.getStatus());
                list.add(dto);
            }

            EasyExcel.write(response.getOutputStream(), ActivityExportDTO.class)
                    .sheet("活动明细列表")
                    .doWrite(list);
        } catch (Exception e) {
            log.error("导出活动明细失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500, \"message\":\"导出活动明细失败\"}");
        }
    }

    /**
     * 导出积分明细
     */
    @SaCheckLogin
    @GetMapping("/points")
    public void exportPoints(HttpServletResponse response) throws IOException {
        AuthUtil.requireAdminId();
        try {
            setExcelResponseProp(response, "学生积分明细报表");

            List<StudentPointRecord> records = pointRecordMapper.selectList(
                    new LambdaQueryWrapper<StudentPointRecord>()
                            .eq(StudentPointRecord::getDeleted, 0)
                            .orderByDesc(StudentPointRecord::getCreateTime)
            );

            Set<Long> studentIds = records.stream()
                    .map(StudentPointRecord::getStudentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Long, Student> studentMap = studentIds.isEmpty() ? Collections.emptyMap()
                    : studentMapper.selectBatchIds(studentIds).stream()
                    .collect(Collectors.toMap(Student::getId, s -> s));

            List<PointRecordExportDTO> list = new ArrayList<>();
            for (StudentPointRecord r : records) {
                Student s = studentMap.get(r.getStudentId());
                PointRecordExportDTO dto = new PointRecordExportDTO();
                dto.setId(r.getId());
                dto.setStudentNo(s != null ? s.getStudentNo() : "-");
                dto.setStudentName(s != null ? s.getName() : "学生");
                dto.setBeforePoints(r.getBeforePoints());
                dto.setChangePoints(r.getChangePoints());
                dto.setAfterPoints(r.getAfterPoints());

                String bizType = switch (r.getType() != null ? r.getType() : 0) {
                    case 1 -> "活动打卡奖励";
                    case 2 -> "商城兑换消费";
                    case 3 -> "系统人工调账";
                    case 4 -> "订单取消返还";
                    default -> "其他流水变动";
                };
                dto.setBizType(bizType);
                dto.setBizRef(StringUtils.hasText(r.getDescription()) ? r.getDescription() : "-");
                dto.setCreateTime(r.getCreateTime());
                list.add(dto);
            }

            EasyExcel.write(response.getOutputStream(), PointRecordExportDTO.class)
                    .sheet("学生积分台账流水")
                    .doWrite(list);
        } catch (Exception e) {
            log.error("导出积分明细失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500, \"message\":\"导出积分明细失败\"}");
        }
    }

    /**
     * 导出学生活动参与/打卡明细
     */
    @SaCheckLogin
    @GetMapping({"/activity-records", "/activities/records"})
    public void exportActivityRecords(
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String activityTitle,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws IOException {
        AuthUtil.requireAdminId();
        try {
            setExcelResponseProp(response, "学生活动打卡参与记录报表");

            // 查询前 10000 条匹配记录用于报表生成
            IPage<ActivityRecordVO> page = studentActivityMapper.selectAdminPage(
                    new Page<>(1, 10000),
                    StringUtils.hasText(studentName) ? studentName.trim() : null,
                    StringUtils.hasText(studentNo) ? studentNo.trim() : null,
                    StringUtils.hasText(activityTitle) ? activityTitle.trim() : null,
                    status
            );

            List<ActivityRecordExportDTO> list = new ArrayList<>();
            for (ActivityRecordVO r : page.getRecords()) {
                ActivityRecordExportDTO dto = new ActivityRecordExportDTO();
                dto.setId(r.getId());
                dto.setStudentNo(r.getStudentNo() != null ? r.getStudentNo() : "-");
                dto.setStudentName(r.getStudentName() != null ? r.getStudentName() : "-");
                dto.setActivityTitle(r.getActivityTitle() != null ? r.getActivityTitle() : "-");

                String statusDesc = switch (r.getStatus() != null ? r.getStatus() : 0) {
                    case 1 -> "已报名(待打卡)";
                    case 2 -> "已打卡出勤(已发分)";
                    case 3 -> "已失效/取消";
                    default -> "其他状态";
                };
                dto.setStatusDesc(statusDesc);
                dto.setPointsAwarded(r.getPointsAwarded() != null ? r.getPointsAwarded() : 0);
                dto.setSignupTime(r.getSignupTime());
                dto.setCheckinTime(r.getCheckinTime());
                dto.setCheckinDistance(r.getCheckinDistance() != null ? r.getCheckinDistance() + "m" : "-");
                dto.setRemark(StringUtils.hasText(r.getRemark()) ? r.getRemark() : "-");
                list.add(dto);
            }

            EasyExcel.write(response.getOutputStream(), ActivityRecordExportDTO.class)
                    .sheet("学生活动打卡记录")
                    .doWrite(list);
        } catch (Exception e) {
            log.error("导出学生活动记录失败", e);
            response.reset();
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.getWriter().println("{\"code\":500, \"message\":\"导出学生活动记录失败\"}");
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
