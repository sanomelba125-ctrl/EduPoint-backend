package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.mapper.CertificateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工具 4: 查询学生本人荣誉结项证书与防伪凭据编号
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryCertificatesTool implements AiTool {

    private final CertificateMapper certificateMapper;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getName() {
        return AiToolEnum.QUERY_CERTIFICATES.getFunctionName();
    }

    @Override
    public String getDescription() {
        return AiToolEnum.QUERY_CERTIFICATES.getDescription();
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
        limitProp.put("description", "获取最新证书记录条数，默认5条，最大20条");
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
            err.put("error", "未检测到登录状态。查询荣誉证书记录属于个人私有数据，请提醒学生先前往平台【个人中心】登录账号。");
            return err.toJSONString();
        }

        try {
            int limit = 5;
            if (arguments != null && arguments.containsKey("limit")) {
                Integer l = arguments.getInteger("limit");
                if (l != null && l > 0) {
                    limit = Math.min(l, 20);
                }
            }

            List<CertificateMyVO> certList = certificateMapper.selectMyList(studentId);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("totalCertificateCount", certList != null ? certList.size() : 0);

            JSONArray list = new JSONArray();
            if (certList != null) {
                int count = 0;
                for (CertificateMyVO cert : certList) {
                    if (count++ >= limit) break;
                    JSONObject item = new JSONObject();
                    item.put("certificateNo", cert.getCertificateNo()); // 唯一防伪编号
                    item.put("title", cert.getTitle());
                    item.put("activityTitle", cert.getActivityTitle());
                    item.put("issuer", cert.getIssuer() != null ? cert.getIssuer() : "校团委/学工处");
                    item.put("issueTime", cert.getIssueTime() != null ? cert.getIssueTime().format(DATE_TIME_FORMATTER) : "");
                    item.put("status", cert.getStatus());
                    item.put("statusText", formatCertStatus(cert.getStatus()));
                    item.put("fileUrl", cert.getFileUrl());
                    list.add(item);
                }
            }

            result.put("certificates", list);
            result.put("returnedCount", list.size());
            return result.toJSONString();
        } catch (Exception e) {
            log.error("执行 QueryCertificatesTool 异常: studentId={}", studentId, e);
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "查询结项证书服务出现异常: " + e.getMessage());
            return err.toJSONString();
        }
    }

    private String formatCertStatus(Integer status) {
        if (status == null) return "有效";
        return switch (status) {
            case 1 -> "官方有效（支持扫码公开验真）";
            case 2 -> "已作废撤销";
            case 3 -> "已过期";
            default -> "有效";
        };
    }
}
