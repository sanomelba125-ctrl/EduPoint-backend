package com.campus.modules.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.ai.dto.QaKnowledgeDTO;
import com.campus.modules.ai.dto.QaKnowledgePageQuery;
import com.campus.modules.ai.vo.AiFaqVO;
import com.campus.modules.system.entity.QaKnowledge;

import java.util.List;

/**
 * AI 客服知识库业务接口
 */
public interface QaKnowledgeService extends IService<QaKnowledge> {

    /**
     * 根据用户提问匹配最佳官方标准问答
     *
     * @param question 用户脱敏后的提问文本
     * @return 命中的官方知识库条目，未高置信度命中则返回 null
     */
    QaKnowledge matchKnowledge(String question);

    /**
     * 累加知识库查阅量
     *
     * @param id 知识库 ID
     */
    void incrementViewCount(Long id);

    /**
     * 获取推荐热门常见问题清单 (供前置推荐胶囊展示)
     *
     * @param limit 条数限制
     * @return 推荐列表
     */
    List<AiFaqVO> getRecommendedFaqs(int limit);

    /**
     * 管理端分页查询知识库条目
     */
    IPage<QaKnowledge> pageKnowledge(QaKnowledgePageQuery query);

    /**
     * 创建知识库条目
     */
    boolean saveKnowledge(QaKnowledgeDTO dto);

    /**
     * 修改知识库条目
     */
    boolean updateKnowledge(QaKnowledgeDTO dto);

    /**
     * 逻辑删除知识库条目
     */
    boolean deleteKnowledge(Long id);

    /**
     * 切换知识库条目启用状态
     */
    boolean toggleStatus(Long id, Integer status);
}
