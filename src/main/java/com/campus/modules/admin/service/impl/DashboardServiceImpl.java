package com.campus.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import com.campus.modules.admin.dto.DashboardOverviewVO;
import com.campus.modules.admin.dto.DashboardTopStudentsVO;
import com.campus.modules.admin.service.DashboardService;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台数据大屏与核心指标统计业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final StudentMapper studentMapper;
    private final StudentActivityMapper studentActivityMapper;
    private final ActivityMapper activityMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public DashboardOverviewVO getOverview() {
        // 1. 学生总数
        Long totalStudents = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getDeleted, 0)
        );

        // 2. 积分流通量 (当前可用积分之和) 与 累计总积分之和 (单条 SQL 聚合求和)
        Long circulatingPointsSum = studentMapper.sumCurrentPoints();
        long circulatingPoints = circulatingPointsSum != null ? circulatingPointsSum : 0L;
        Long totalPointsSum = studentMapper.sumTotalPoints();
        long totalPoints = totalPointsSum != null ? totalPointsSum : 0L;

        // 3. 报名总数与打卡成功总人次
        Long totalSignups = studentActivityMapper.selectCount(
                new LambdaQueryWrapper<StudentActivity>().eq(StudentActivity::getDeleted, 0)
        );
        Long totalCheckins = studentActivityMapper.selectCount(
                new LambdaQueryWrapper<StudentActivity>()
                        .eq(StudentActivity::getDeleted, 0)
                        .and(w -> w.eq(StudentActivity::getStatus, 2).or().isNotNull(StudentActivity::getCheckinTime))
        );

        // 计算平均打卡签到率
        String checkinRate = "0.0%";
        if (totalSignups != null && totalSignups > 0 && totalCheckins != null) {
            double rate = Math.min(100.0, (totalCheckins * 100.0) / totalSignups);
            checkinRate = String.format(Locale.US, "%.1f%%", rate);
        }

        // 4. 活动总数
        Long totalActivities = activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().eq(Activity::getDeleted, 0)
        );

        // 5. 活动分类分布统计 (单条 GROUP BY 聚合查询)
        List<Map<String, Object>> categoryGroupList = activityMapper.countGroupByCategory();
        Map<String, Long> categoryCountMap = new HashMap<>();
        if (categoryGroupList != null) {
            for (Map<String, Object> map : categoryGroupList) {
                String cat = map.get("category") != null ? map.get("category").toString() : "OTHER";
                Long cnt = map.get("cnt") instanceof Number n ? n.longValue() : 0L;
                categoryCountMap.put(cat, cnt);
            }
        }

        Map<String, String> categoryNames = new LinkedHashMap<>();
        categoryNames.put("ACADEMIC", "学术讲座");
        categoryNames.put("VOLUNTEER", "志愿服务");
        categoryNames.put("SPORTS", "文体竞技");
        categoryNames.put("PRACTICE", "社会实践");
        categoryNames.put("OTHER", "其他活动");

        List<DashboardOverviewVO.CategoryStatVO> categoryStats = new ArrayList<>();
        for (Map.Entry<String, String> entry : categoryNames.entrySet()) {
            categoryStats.add(DashboardOverviewVO.CategoryStatVO.builder()
                    .category(entry.getKey())
                    .name(entry.getValue())
                    .count(categoryCountMap.getOrDefault(entry.getKey(), 0L))
                    .build());
        }

        // 6. 最近 7 天打卡趋势统计 (单查近 7 天有效记录，限制时间范围与轻量字段投影)
        LocalDate today = LocalDate.now();
        List<DashboardOverviewVO.DailyTrendVO> weeklyTrend = new ArrayList<>();
        LocalDateTime sevenDaysAgoStart = today.minusDays(6).atStartOfDay();
        List<StudentActivity> checkinList = studentActivityMapper.selectList(
                new LambdaQueryWrapper<StudentActivity>()
                        .select(StudentActivity::getCheckinTime)
                        .eq(StudentActivity::getDeleted, 0)
                        .and(w -> w.eq(StudentActivity::getStatus, 2).or().isNotNull(StudentActivity::getCheckinTime))
                        .ge(StudentActivity::getCheckinTime, sevenDaysAgoStart)
        );

        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String dateLabel = d.format(DATE_FORMATTER);
            long count = checkinList.stream().filter(item -> {
                LocalDateTime t = item.getCheckinTime();
                return t != null && t.toLocalDate().equals(d);
            }).count();

            weeklyTrend.add(DashboardOverviewVO.DailyTrendVO.builder()
                    .date(dateLabel)
                    .count(count)
                    .build());
        }

        return DashboardOverviewVO.builder()
                .totalStudents(totalStudents)
                .totalCheckins(totalCheckins)
                .checkinRate(checkinRate)
                .circulatingPoints(circulatingPoints)
                .totalPoints(totalPoints)
                .totalActivities(totalActivities)
                .categoryDistribution(categoryStats)
                .weeklyCheckinTrend(weeklyTrend)
                .build();
    }

    @Override
    public DashboardTopStudentsVO getTopStudentsAndRecentCheckins() {
        // 1. Top 5 榜单
        List<Student> topStudentsList = studentMapper.selectList(
                new LambdaQueryWrapper<Student>()
                        .eq(Student::getDeleted, 0)
                        .orderByDesc(Student::getTotalPoints)
                        .orderByAsc(Student::getId)
                        .last("LIMIT 5")
        );

        List<DashboardTopStudentsVO.TopStudentVO> topVos = new ArrayList<>();
        int rank = 1;
        for (Student s : topStudentsList) {
            topVos.add(DashboardTopStudentsVO.TopStudentVO.builder()
                    .rank(rank++)
                    .id(s.getId())
                    .studentNo(s.getStudentNo())
                    .name(s.getName())
                    .className(s.getClassName())
                    .points(s.getTotalPoints() == null ? 0 : s.getTotalPoints())
                    .currentPoints(s.getCurrentPoints() == null ? 0 : s.getCurrentPoints())
                    .avatar(s.getAvatar())
                    .build());
        }

        // 2. 最新打卡流水 (取最近 10 条)
        List<StudentActivity> recentActs = studentActivityMapper.selectList(
                new LambdaQueryWrapper<StudentActivity>()
                        .eq(StudentActivity::getDeleted, 0)
                        .and(w -> w.eq(StudentActivity::getStatus, 2).or().isNotNull(StudentActivity::getCheckinTime))
                        .orderByDesc(StudentActivity::getCheckinTime)
                        .orderByDesc(StudentActivity::getId)
                        .last("LIMIT 10")
        );

        List<DashboardTopStudentsVO.RecentCheckinVO> checkinVos = new ArrayList<>();
        if (!recentActs.isEmpty()) {
            Set<Long> studentIds = recentActs.stream().map(StudentActivity::getStudentId).collect(Collectors.toSet());
            Set<Long> activityIds = recentActs.stream().map(StudentActivity::getActivityId).collect(Collectors.toSet());

            Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                    .collect(Collectors.toMap(Student::getId, s -> s));
            Map<Long, Activity> activityMap = activityMapper.selectBatchIds(activityIds).stream()
                    .collect(Collectors.toMap(Activity::getId, a -> a));

            for (StudentActivity act : recentActs) {
                Student s = studentMap.get(act.getStudentId());
                Activity a = activityMap.get(act.getActivityId());

                String timeStr = act.getCheckinTime() != null ? act.getCheckinTime().format(TIME_FORMATTER) : "-";

                checkinVos.add(DashboardTopStudentsVO.RecentCheckinVO.builder()
                        .id(act.getId())
                        .studentNo(s != null ? s.getStudentNo() : "-")
                        .name(s != null ? s.getName() : "同学")
                        .className(s != null ? s.getClassName() : "-")
                        .activity(a != null ? a.getTitle() : "校园活动")
                        .points(act.getPointsAwarded() == null ? (a != null && a.getPoints() != null ? a.getPoints() : 10) : act.getPointsAwarded())
                        .time(timeStr)
                        .status(act.getStatus())
                        .build());
            }
        }

        return DashboardTopStudentsVO.builder()
                .topStudents(topVos)
                .recentCheckins(checkinVos)
                .build();
    }
}
