package com.campus.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.ai.dto.QaKnowledgeDTO;
import com.campus.modules.ai.dto.QaKnowledgePageQuery;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.vo.AiFaqVO;
import com.campus.modules.system.entity.QaKnowledge;
import com.campus.modules.system.mapper.QaKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 客服知识库业务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaKnowledgeServiceImpl extends ServiceImpl<QaKnowledgeMapper, QaKnowledge>
        implements QaKnowledgeService {

    @Override
    public QaKnowledge matchKnowledge(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }

        String q = question.trim().toLowerCase();

        // 检索所有处于启用状态的知识库条目
        List<QaKnowledge> activeList = this.list(new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getStatus, 1)
                .eq(QaKnowledge::getDeleted, 0)
                .orderByAsc(QaKnowledge::getSortOrder)
                .orderByDesc(QaKnowledge::getViewCount));

        if (activeList == null || activeList.isEmpty()) {
            return null;
        }

        // 计算相似度打分
        QaKnowledge bestMatch = null;
        int maxScore = 0;

        for (QaKnowledge item : activeList) {
            int score = 0;
            String itemQuestion = item.getQuestion() != null ? item.getQuestion().trim().toLowerCase() : "";
            String itemKeywords = item.getKeywords() != null ? item.getKeywords().trim().toLowerCase() : "";

            // 1. 问题完全匹配或高度包含
            if (itemQuestion.equals(q)) {
                score += 120;
            } else if (q.contains(itemQuestion) || (itemQuestion.length() >= 4 && itemQuestion.contains(q))) {
                score += 80;
            }

            // 2. 关键词匹配打分
            if (StringUtils.hasText(itemKeywords)) {
                String[] kwArr = itemKeywords.split("[,，;；/\\s]+");
                for (String kw : kwArr) {
                    if (StringUtils.hasText(kw) && kw.length() >= 2) {
                        if (q.contains(kw)) {
                            score += 35; // 命中核心词
                        }
                    }
                }
            }

            // 阈值判定：达到 35 分（命中核心关键词或问题短语）且分最高
            if (score >= 35 && score > maxScore) {
                maxScore = score;
                bestMatch = item;
            }
        }

        if (bestMatch != null) {
            log.info("【AI 知识库】用户问题 '{}' 命中知识库条目 [ID:{}] '{}'，匹配得分: {}",
                    question, bestMatch.getId(), bestMatch.getQuestion(), maxScore);
        }

        return bestMatch;
    }

    @Override
    public void incrementViewCount(Long id) {
        if (id == null) {
            return;
        }
        try {
            QaKnowledge item = this.getById(id);
            if (item != null) {
                item.setViewCount(item.getViewCount() == null ? 1 : item.getViewCount() + 1);
                this.updateById(item);
            }
        } catch (Exception e) {
            log.warn("更新知识库查阅次数失败: id={}", id, e);
        }
    }

    @Override
    public List<AiFaqVO> getRecommendedFaqs(int limit) {
        int maxLimit = limit <= 0 ? 5 : Math.min(limit, 20);
        List<QaKnowledge> list = this.list(new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getStatus, 1)
                .eq(QaKnowledge::getDeleted, 0)
                .orderByAsc(QaKnowledge::getSortOrder)
                .orderByDesc(QaKnowledge::getViewCount)
                .last("LIMIT " + maxLimit));

        return list.stream().map(item -> {
            AiFaqVO vo = new AiFaqVO();
            vo.setId(item.getId());
            vo.setCategory(item.getCategory());
            vo.setQuestion(item.getQuestion());
            vo.setKeywords(item.getKeywords());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public IPage<QaKnowledge> pageKnowledge(QaKnowledgePageQuery query) {
        long current = query.getPage() == null ? 1 : Math.max(query.getPage(), 1);
        long size = query.getSize() == null ? 10 : Math.min(Math.max(query.getSize(), 1), 100);

        Page<QaKnowledge> page = new Page<>(current, size);
        LambdaQueryWrapper<QaKnowledge> wrapper = new LambdaQueryWrapper<QaKnowledge>()
                .eq(QaKnowledge::getDeleted, 0)
                .eq(StringUtils.hasText(query.getCategory()), QaKnowledge::getCategory, query.getCategory())
                .eq(query.getStatus() != null, QaKnowledge::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), w ->
                        w.like(QaKnowledge::getQuestion, query.getKeyword().trim())
                                .or()
                                .like(QaKnowledge::getKeywords, query.getKeyword().trim())
                                .or()
                                .like(QaKnowledge::getAnswer, query.getKeyword().trim())
                )
                .orderByAsc(QaKnowledge::getSortOrder)
                .orderByDesc(QaKnowledge::getId);

        return this.page(page, wrapper);
    }

    @Override
    public boolean saveKnowledge(QaKnowledgeDTO dto) {
        if (!StringUtils.hasText(dto.getQuestion()) || !StringUtils.hasText(dto.getAnswer())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "问题和标准答案不能为空");
        }

        QaKnowledge entity = new QaKnowledge();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getCategory() == null) {
            entity.setCategory("OTHER");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(50);
        }
        if (entity.getViewCount() == null) {
            entity.setViewCount(0);
        }
        entity.setDeleted(0);
        return this.save(entity);
    }

    @Override
    public boolean updateKnowledge(QaKnowledgeDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "知识库条目ID不能为空");
        }
        QaKnowledge exist = this.getById(dto.getId());
        if (exist == null || exist.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "知识库条目不存在或已被删除");
        }

        QaKnowledge entity = new QaKnowledge();
        BeanUtils.copyProperties(dto, entity);
        return this.updateById(entity);
    }

    @Override
    public boolean deleteKnowledge(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "ID不能为空");
        }
        return this.removeById(id);
    }

    @Override
    public boolean toggleStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "参数错误");
        }
        QaKnowledge item = this.getById(id);
        if (item == null || item.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "知识库条目不存在");
        }
        item.setStatus(status == 1 ? 1 : 0);
        return this.updateById(item);
    }
}
