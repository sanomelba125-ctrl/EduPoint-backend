package com.campus.modules.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.result.Result;
import com.campus.modules.system.dto.NoticePageQuery;
import com.campus.modules.system.dto.SysNoticeVO;
import com.campus.modules.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统消息通知控制器
 * 双端通用：支持管理端 (receiverType=1) 与学生端 (receiverType=2)
 */
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    /**
     * 分页获取当前登录用户的通知列表
     * GET /api/notice/my-list
     */
    @GetMapping("/my-list")
    public Result<IPage<SysNoticeVO>> getMyNotices(NoticePageQuery query) {
        StpUtil.checkLogin();
        if (query == null) {
            query = new NoticePageQuery();
        }
        IPage<SysNoticeVO> page = sysNoticeService.getMyNotices(query);
        return Result.success(page);
    }

    /**
     * 获取当前登录用户未读通知条数
     * GET /api/notice/unread-count
     */
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        StpUtil.checkLogin();
        Long unreadCount = sysNoticeService.getMyUnreadCount();
        return Result.success(unreadCount);
    }

    /**
     * 标记单条通知为已读
     * PUT /api/notice/{id}/read
     */
    @PutMapping("/{id}/read")
    public Result<Boolean> markAsRead(@PathVariable("id") Long id) {
        StpUtil.checkLogin();
        boolean success = sysNoticeService.markAsRead(id);
        return Result.success(success);
    }

    /**
     * 一键标记当前用户所有通知为已读
     * PUT /api/notice/read-all
     */
    @PutMapping("/read-all")
    public Result<Boolean> markAllAsRead() {
        StpUtil.checkLogin();
        boolean success = sysNoticeService.markAllAsRead();
        return Result.success(success);
    }

    /**
     * 删除单条通知 (逻辑删除)
     * DELETE /api/notice/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteNotice(@PathVariable("id") Long id) {
        StpUtil.checkLogin();
        boolean success = sysNoticeService.deleteNotice(id);
        return Result.success(success);
    }
}
