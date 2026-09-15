package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.system.entity.QaKnowledge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工具 5: 从官方知识库中检索打卡、积分、选拔与评奖规则解答
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AiTool {

    private final QaKnowledgeService qaKnowledgeService;

    @Override
    public String getName() {
        return AiToolEnum.SEARCH_KNOWLEDGE.getFunctionName();
    }

    @Override
    public String getDescription() {
        return AiToolEnum.SEARCH_KNOWLEDGE.getDescription();
    }

    @Override
    public JSONObject getToolDefinition() {
        JSONObject function = new JSONObject();
        function.put("name", getName());
        function.put("description", getDescription());

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");

        JSONObject properties = new JSONObject();

        JSONObject keywordProp = new JSONObject();
        keywordProp.put("type", "string");
        keywordProp.put("description", "检索关键词或问题描述，例如：打卡失败、积分规则、提货流程、候补排队");
        properties.put("keyword", keywordProp);

        JSONObject categoryProp = new JSONObject();
        categoryProp.put("type", "string");
        categoryProp.put("description", "分类过滤：SCORE(积分说明), ACTIVITY(活动打卡), GIFT(礼品兑换), RULE(平台规则), OTHER(其他)");
        JSONArray catEnum = new JSONArray();
        catEnum.add("SCORE");
        catEnum.add("ACTIVITY");
        catEnum.add("GIFT");
        catEnum.add("RULE");
        catEnum.add("OTHER");
        categoryProp.put("enum", catEnum);
        properties.put("category", categoryProp);

        parameters.put("properties", properties);

        JSONArray required = new JSONArray();
        required.add("keyword");
        parameters.put("required", required);

        function.put("parameters", parameters);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    @Override
    public String execute(Long studentId, JSONObject arguments) {
        String keyword = arguments != null ? arguments.getString("keyword") : "";
        String category = arguments != null ? arguments.getString("category") : null;

        if (!StringUtils.hasText(keyword)) {
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "未提供检索关键词");
            return err.toJSONString();
        }

        try {
            LambdaQueryWrapper<QaKnowledge> wrapper = new LambdaQueryWrapper<QaKnowledge>()
                    .eq(QaKnowledge::getStatus, 1)
                    .eq(QaKnowledge::getDeleted, 0)
                    .eq(StringUtils.hasText(category), QaKnowledge::getCategory, category)
                    .and(w -> w.like(QaKnowledge::getQuestion, keyword)
                            .or().like(QaKnowledge::getKeywords, keyword)
                            .or().like(QaKnowledge::getAnswer, keyword))
                    .orderByAsc(QaKnowledge::getSortOrder)
                    .orderByDesc(QaKnowledge::getViewCount)
                    .last("LIMIT 3");

            List<QaKnowledge> list = qaKnowledgeService.list(wrapper);

            // 若模糊匹配未命中，且包含多个字，尝试对前几个字检索或匹配推荐
            if (list == null || list.isEmpty()) {
                QaKnowledge bestMatch = qaKnowledgeService.matchKnowledge(keyword);
                if (bestMatch != null) {
                    list = List.of(bestMatch);
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("keyword", keyword);

            JSONArray items = new JSONArray();
            if (list != null) {
                for (QaKnowledge q : list) {
                    JSONObject item = new JSONObject();
                    item.put("id", q.getId());
                    item.put("category", q.getCategory());
                    item.put("question", q.getQuestion());
                    item.put("officialAnswer", q.getAnswer());
                    items.add(item);
                }
            }
            result.put("matchedFaqs", items);
            result.put("matchedCount", items.size());

            return result.toJSONString();
        } catch (Exception e) {
            log.error("执行 SearchKnowledgeTool 异常: keyword={}", keyword, e);
            JSONObject err = new JSONObject();
            err.put("success", false);
            err.put("error", "检索知识库服务出现异常: " + e.getMessage());
            return err.toJSONString();
        }
    }
}
