package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工具 2: 查询学生礼品兑换订单与提货核销码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryOrdersTool implements AiTool {

    private final StudentGiftMapper studentGiftMapper;
    private final GiftMapper giftMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return AiToolEnum.QUERY_ORDERS.getFunctionName();
    }

    @Override
    public String getDescription() {
        return AiToolEnum.QUERY_ORDERS.getDescription();
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
        statusProp.put("description", "订单状态过滤: 1-待核销/待提货, 2-已核销/已送达, 3-已取消, 0或不传为查询全部");
        JSONArray statusEnum = new JSONArray();
        statusEnum.add(0);
        statusEnum.add(1);
        statusEnum.add(2);
        statusEnum.add(3);
        statusProp.put("enum", statusEnum);
        properties.put("status", statusProp);

        JSONObject limitProp = new JSONObject();
        limitProp.put("type", "integer");
        limitProp.put("description", "查询订单条数，默认5条，最大20条");
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
    public String execute(Long studentId, JSONObject arguments) {
        if (studentId == null) {
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "未检测到登录状态。查询礼品兑换订单属于个人私有数据，请提醒学生先前往平台【个人中心】登录账号。");
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
                        limit = Math.min(l, 20);
                    }
                }
            }

            LambdaQueryWrapper<StudentGift> wrapper = new LambdaQueryWrapper<StudentGift>()
                    .eq(StudentGift::getStudentId, studentId)
                    .eq(status != null, StudentGift::getStatus, status)
                    .eq(StudentGift::getDeleted, 0)
                    .orderByDesc(StudentGift::getCreateTime)
                    .last("LIMIT " + limit);

            List<StudentGift> orders = studentGiftMapper.selectList(wrapper);

            // 统计待提货订单数
            Long pendingCount = studentGiftMapper.selectCount(new LambdaQueryWrapper<StudentGift>()
                    .eq(StudentGift::getStudentId, studentId)
                    .eq(StudentGift::getStatus, 1)
                    .eq(StudentGift::getDeleted, 0));

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("pendingPickupCount", pendingCount != null ? pendingCount : 0);

            JSONArray orderList = new JSONArray();
            for (StudentGift order : orders) {
                JSONObject item = new JSONObject();
                item.put("orderNo", order.getOrderNo());
                item.put("quantity", order.getQuantity());
                item.put("pointsCost", order.getPointsCost());
                item.put("pickupCode", order.getPickupCode()); // 关键核销码
                item.put("status", order.getStatus());
                item.put("statusText", formatStatus(order.getStatus()));
                item.put("createTime", order.getCreateTime() != null ? order.getCreateTime().format(DATE_TIME_FORMATTER) : "");
                if (order.getDeliveryTime() != null) {
                    item.put("deliveryTime", order.getDeliveryTime().format(DATE_TIME_FORMATTER));
                }

                // 关联礼品名称
                if (order.getGiftId() != null) {
                    Gift gift = giftMapper.selectById(order.getGiftId());
                    item.put("giftName", gift != null ? gift.getName() : "校园礼品");
                    item.put("giftDescription", gift != null && gift.getDescription() != null ? gift.getDescription() : "");
                } else {
                    item.put("giftName", "校园礼品");
                }
                orderList.add(item);
            }

            result.put("orders", orderList);
            result.put("returnedCount", orderList.size());
            return result.toJSONString();
        } catch (Exception e) {
            log.error("执行 QueryOrdersTool 异常: studentId={}", studentId, e);
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "查询兑换订单服务出现异常: " + e.getMessage());
            return err.toJSONString();
        }
    }

    private String formatStatus(Integer status) {
        if (status == null) return "未知状态";
        return switch (status) {
            case 1 -> "待核销/待提货（请出示提货码到线下服务台领取）";
            case 2 -> "已核销发放/已完成";
            case 3 -> "已取消（积分已退还）";
            default -> "未知状态";
        };
    }
}
