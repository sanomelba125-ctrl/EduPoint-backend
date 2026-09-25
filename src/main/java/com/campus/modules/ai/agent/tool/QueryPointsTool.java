package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.entity.PointRecordType;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工具 1: 查询学生个人积分余额及收支明细
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPointsTool implements AiTool {

    private final StudentMapper studentMapper;
    private final StudentPointRecordMapper studentPointRecordMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return AiToolEnum.QUERY_POINTS.getFunctionName();
    }

    @Override
    public String getDescription() {
        return AiToolEnum.QUERY_POINTS.getDescription();
    }

    @Override
    public JSONObject getToolDefinition() {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();

        JSONObject limitProp = new JSONObject();
        limitProp.put("type", "integer");
        limitProp.put("description", "获取最新积分流水记录条数，默认5条，最大20条");
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
            err.put("error", "未检测到登录状态。查询积分余额属于个人私有数据，请提醒学生先前往平台【个人中心】登录账号。");
            return err.toJSONString();
        }

        try {
            Student student = studentMapper.selectById(studentId);
            if (student == null) {
                JSONObject err = new JSONObject();
                err.put("success", false);
                err.put("error", "学籍数据库中未查询到该学生档案记录");
                return err.toJSONString();
            }

            int limit = 5;
            if (arguments != null && arguments.containsKey("limit")) {
                Integer l = arguments.getInteger("limit");
                if (l != null && l > 0) {
                    limit = Math.min(l, 10);
                }
            }

            List<StudentPointRecord> records = studentPointRecordMapper.selectList(
                    new LambdaQueryWrapper<StudentPointRecord>()
                            .eq(StudentPointRecord::getStudentId, studentId)
                            .eq(StudentPointRecord::getDeleted, 0)
                            .orderByDesc(StudentPointRecord::getCreateTime)
                            .last("LIMIT " + limit)
            );

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("currentAvailablePoints", student.getCurrentPoints() != null ? student.getCurrentPoints() : 0);
            result.put("historicalTotalPoints", student.getTotalPoints() != null ? student.getTotalPoints() : 0);

            JSONArray list = new JSONArray();
            for (StudentPointRecord r : records) {
                JSONObject item = new JSONObject();
                item.put("changePoints", (r.getChangePoints() != null && r.getChangePoints() > 0 ? "+" : "") + r.getChangePoints());
                item.put("typeText", formatTypeName(r.getType()));
                item.put("description", r.getDescription());
                item.put("balanceAfter", r.getAfterPoints());
                item.put("createTime", r.getCreateTime() != null ? r.getCreateTime().format(DATE_TIME_FORMATTER) : "");
                list.add(item);
            }
            result.put("recentTransactions", list);
            result.put("totalReturnedCount", list.size());

            return result.toJSONString();
        } catch (Exception e) {
            log.error("执行 QueryPointsTool 异常: studentId={}", studentId, e);
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "查询积分信息服务出现异常: " + e.getMessage());
            return err.toJSONString();
        }
    }

    private String formatTypeName(Integer type) {
        if (type == null) {
            return "未知流水";
        }
        return PointRecordType.of(type).getDescription();
    }
}
