package com.campus.modules.ai.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.constant.UserRole;
import com.campus.common.result.Result;
import com.campus.modules.ai.dto.QaKnowledgeDTO;
import com.campus.modules.ai.dto.QaKnowledgePageQuery;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.system.entity.QaKnowledge;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端知识库维护控制器
 * 对应设计: A13 AI 客服运维 / 维护平台规则与常见问题知识库
 */
@RestController
@RequestMapping("/admin/qa-knowledge")
@SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN}, mode = SaMode.OR)
@RequiredArgsConstructor
public class QaKnowledgeAdminController {

    private final QaKnowledgeService qaKnowledgeService;

    /**
     * 分页查询知识库条目
     * GET /api/admin/qa-knowledge/page
     */
    @GetMapping("/page")
    public Result<IPage<QaKnowledge>> page(QaKnowledgePageQuery query) {
        if (query == null) {
            query = new QaKnowledgePageQuery();
        }
        IPage<QaKnowledge> page = qaKnowledgeService.pageKnowledge(query);
        return Result.success(page);
    }

    /**
     * 获取单条知识库详情
     * GET /api/admin/qa-knowledge/{id}
     */
    @GetMapping("/{id}")
    public Result<QaKnowledge> getById(@PathVariable("id") Long id) {
        QaKnowledge item = qaKnowledgeService.getById(id);
        return Result.success(item);
    }

    /**
     * 新增知识库条目
     * POST /api/admin/qa-knowledge
     */
    @PostMapping
    public Result<Boolean> create(@RequestBody QaKnowledgeDTO dto) {
        boolean success = qaKnowledgeService.saveKnowledge(dto);
        return Result.success("知识库条目新增成功", success);
    }

    /**
     * 修改知识库条目
     * PUT /api/admin/qa-knowledge
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody QaKnowledgeDTO dto) {
        boolean success = qaKnowledgeService.updateKnowledge(dto);
        return Result.success("知识库条目更新成功", success);
    }

    /**
     * 删除知识库条目
     * DELETE /api/admin/qa-knowledge/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        boolean success = qaKnowledgeService.deleteKnowledge(id);
        return Result.success("知识库条目已删除", success);
    }

    /**
     * 切换知识库条目状态 (启用/停用)
     * PUT /api/admin/qa-knowledge/{id}/status?status=1
     */
    @PutMapping("/{id}/status")
    public Result<Boolean> toggleStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        boolean success = qaKnowledgeService.toggleStatus(id, status);
        return Result.success("状态更新成功", success);
    }
}
