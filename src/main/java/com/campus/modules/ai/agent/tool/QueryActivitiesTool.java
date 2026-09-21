package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityWaitlist;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.mapper.ActivityWaitlistMapper;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工具 3: 查询学生已报名、已打卡或排队候补中的校园活动
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryActivitiesTool implements AiTool {

    private final StudentActivityMapper studentActivityMapper;
    private final ActivityMapper activityMapper;
    private final ActivityWaitlistMapper activityWaitlistMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return AiToolEnum.QUERY_ACTIVITIES.getFunctionName();
    }

    @Override
    public String getDescription() {
        return AiToolEnum.QUERY_ACTIVITIES.getDescription();
    }

    @Override
    public JSONObject getToolDefinition() {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();

        JSONObject statusProp = new JSONObject();
        statusProp.put("type", "integer");
        statusProp.put("description", "活动参与状态过滤: 1-已报名待打卡, 2-已打卡完成已领积分, 4-满额排队候补中, 0或不传为查询全部");
        JSONArray statusEnum = new JSONArray();
        statusEnum.add(0);
        statusEnum.add(1);
        statusEnum.add(2);
        statusEnum.add(4);
        statusProp.put("enum", statusEnum);
        properties.put("status", statusProp);

        JSONObject limitProp = new JSONObject();
        limitProp.put("type", "integer");
        limitProp.put("description", "查询活动记录条数，默认5条，最大20条");
        limitProp.put("default", 5);
        properties.put("limit", limitProp);

        parameters.put("properties", properties);
        parameters.put("required", new JSONArray());

        function.put("parameters", parameters);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @Override
    public String execute(Long studentId, JSONObject arguments) {
        if (studentId == null) {
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "未检测到登录状态。查询个人活动与候补记录属于个人私有数据，请提醒学生先前往平台【个人中心】登录账号。");
            return err.toJSONString();
        }

        try {
            Integer status = null;
            int limit = 5;
            if (arguments != null) {
                if (arguments.containsKey("status")) {
                    Integer s = arguments.getInteger("status");
                    if (s != null && s > 0) {
                        status = s;
                    }
                }
                if (arguments.containsKey("limit")) {
                    Integer l = arguments.getInteger("limit");
                    if (l != null && l > 0) {
                        limit = Math.min(l, 10);
                    }
                }
            }

            JSONArray list = new JSONArray();

            // 1. 若非仅查候补 (status != 4)，查询报名及打卡活动
            if (status == null || status != 4) {
                LambdaQueryWrapper<StudentActivity> wrapper = new LambdaQueryWrapper<StudentActivity>()
                        .eq(StudentActivity::getStudentId, studentId)
                        .eq(status != null, StudentActivity::getStatus, status)
                        .eq(StudentActivity::getDeleted, 0)
                        .orderByDesc(StudentActivity::getSignupTime)
                        .last("LIMIT " + limit);

                List<StudentActivity> activities = studentActivityMapper.selectList(wrapper);
                for (StudentActivity sa : activities) {
                    JSONObject item = new JSONObject();
                    Activity act = activityMapper.selectById(sa.getActivityId());
                    item.put("activityTitle", act != null ? act.getTitle() : "校园活动");
                    item.put("category", act != null ? act.getCategory() : "");
                    item.put("locationName", act != null ? act.getLocationName() : "");
                    item.put("rewardPoints", act != null ? act.getPoints() : 0);
                    item.put("status", sa.getStatus());
                    item.put("statusText", formatActivityStatus(sa.getStatus()));
                    item.put("signupTime", sa.getSignupTime() != null ? sa.getSignupTime().format(DATE_TIME_FORMATTER) : "");
                    if (sa.getCheckinTime() != null) {
                        item.put("checkinTime", sa.getCheckinTime().format(DATE_TIME_FORMATTER));
                        item.put("pointsAwarded", sa.getPointsAwarded() != null ? sa.getPointsAwarded() : 0);
                    }
                    list.add(item);
                }
            }

            // 2. 若查询全部或仅查候补 (status == null || status == 4)，追加排队候补信息
            if ((status == null || status == 4) && list.size() < limit) {
                List<ActivityWaitlist> waitlists = activityWaitlistMapper.selectList(
                        new LambdaQueryWrapper<ActivityWaitlist>()
                                .eq(ActivityWaitlist::getStudentId, studentId)
                                .eq(ActivityWaitlist::getStatus, 1) // 1-排队中
                                .eq(ActivityWaitlist::getDeleted, 0)
                                .orderByDesc(ActivityWaitlist::getWaitTime)
                );

                for (ActivityWaitlist w : waitlists) {
                    if (list.size() >= limit) break;
                    JSONObject item = new JSONObject();
                    Activity act = activityMapper.selectById(w.getActivityId());
                    item.put("activityTitle", act != null ? act.getTitle() : "校园活动");
                    item.put("category", act != null ? act.getCategory() : "");
                    item.put("locationName", act != null ? act.getLocationName() : "");
                    item.put("rewardPoints", act != null ? act.getPoints() : 0);
                    item.put("status", 4);

                    // 计算当前实际排位顺位
                    Long rank = activityWaitlistMapper.selectCount(new LambdaQueryWrapper<ActivityWaitlist>()
                            .eq(ActivityWaitlist::getActivityId, w.getActivityId())
                            .eq(ActivityWaitlist::getStatus, 1)
                            .eq(ActivityWaitlist::getDeleted, 0)
                            .le(ActivityWaitlist::getQueueNumber, w.getQueueNumber()));
                    int queuePos = (rank != null && rank > 0) ? rank.intValue() : 1;

                    item.put("statusText", String.format("活动名额已满，排队候补中（当前顺位第 %d 位）", queuePos));
                    item.put("queueNumber", queuePos);
                    item.put("waitTime", w.getWaitTime() != null ? w.getWaitTime().format(DATE_TIME_FORMATTER) : "");
                    list.add(item);
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("activities", list);
            result.put("returnedCount", list.size());
            return result.toJSONString();
        } catch (Exception e) {
            log.error("执行 QueryActivitiesTool 异常: studentId={}", studentId, e);
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "查询活动与候补记录服务出现异常: " + e.getMessage());
            return err.toJSONString();
        }
    }

    private String formatActivityStatus(Integer status) {
        if (status == null) return "未知状态";
        return switch (status) {
            case 1 -> "已成功报名，待现场扫码打卡";
            case 2 -> "已打卡完成，积分已到账";
            case 3 -> "已取消报名";
            default -> "未知状态";
        };
    }
}
