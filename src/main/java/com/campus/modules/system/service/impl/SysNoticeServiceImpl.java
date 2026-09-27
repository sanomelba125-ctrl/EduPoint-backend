package com.campus.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.websocket.WebSocketServer;
import com.campus.modules.system.dto.NoticePageQuery;
import com.campus.modules.system.dto.SysNoticeVO;
import com.campus.modules.system.entity.SysNotice;
import com.campus.modules.system.mapper.SysNoticeMapper;
import com.campus.modules.system.mapper.SysNoticeReadMapper;
import com.campus.modules.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SysNotice 系统通知服务实现类
 * 落地 P2-9：全校广播通知读写分离治理，解耦个人私信与全校广播
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final SysNoticeReadMapper sysNoticeReadMapper;

    @Override
    public IPage<SysNoticeVO> getMyNotices(NoticePageQuery query) {
        UserIdentity identity = getCurrentUserIdentity();
        if (query == null) {
            query = new NoticePageQuery();
        }
        long current = query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 100);
        Page<SysNoticeVO> page = new Page<>(current, size);

        IPage<SysNoticeVO> voPage = baseMapper.selectUserNoticePage(page, identity.receiverId, identity.receiverType, query);
        if (voPage != null && voPage.getRecords() != null) {
            for (SysNoticeVO vo : voPage.getRecords()) {
                vo.setTypeName(resolveTypeName(vo.getType()));
            }
        }
        return voPage;
    }

    @Override
    public Long getMyUnreadCount() {
        UserIdentity identity = getCurrentUserIdentity();
        Long count = baseMapper.countUserUnread(identity.receiverId, identity.receiverType);
        return count != null ? count : 0L;
    }

    @Override
    public boolean markAsRead(Long id) {
        UserIdentity identity = getCurrentUserIdentity();
        SysNotice notice = this.getById(id);
        if (notice == null || !notice.getReceiverType().equals(identity.receiverType)) {
            return false;
        }
        Long recId = notice.getReceiverId() != null ? notice.getReceiverId() : 0L;
        if (recId.equals(0L)) {
            // 全校公共广播：仅向 sys_notice_read 记录当前用户已读，不污染主表，不影响其他用户
            sysNoticeReadMapper.upsertReadRecord(id, identity.receiverId, identity.receiverType);
            return true;
        }
        if (!recId.equals(identity.receiverId)) {
            return false;
        }
        if (Integer.valueOf(1).equals(notice.getIsRead())) {
            return true;
        }

        notice.setIsRead(1);
        notice.setReadTime(LocalDateTime.now());
        return this.updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAllAsRead() {
        UserIdentity identity = getCurrentUserIdentity();

        // 1. 批量标记当前用户的个人私信为已读
        LambdaUpdateWrapper<SysNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysNotice::getReceiverType, identity.receiverType)
                .eq(SysNotice::getReceiverId, identity.receiverId)
                .eq(SysNotice::getIsRead, 0)
                .set(SysNotice::getIsRead, 1)
                .set(SysNotice::getReadTime, LocalDateTime.now());
        this.update(updateWrapper);

        // 2. 批量将当前用户未读的公共广播关联写入 sys_notice_read
        sysNoticeReadMapper.markAllBroadcastAsRead(identity.receiverId, identity.receiverType);
        return true;
    }

    @Override
    public boolean deleteNotice(Long id) {
        UserIdentity identity = getCurrentUserIdentity();
        SysNotice notice = this.getById(id);
        if (notice == null || !notice.getReceiverType().equals(identity.receiverType)) {
            return false;
        }
        Long recId = notice.getReceiverId() != null ? notice.getReceiverId() : 0L;
        if (recId.equals(0L)) {
            // 全校公共广播：普通用户删除仅在 sys_notice_read 中软隐藏，杜绝全校公告被删空
            sysNoticeReadMapper.upsertDeletedRecord(id, identity.receiverId, identity.receiverType);
            return true;
        }
        if (!recId.equals(identity.receiverId)) {
            return false;
        }
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAndSendNotice(Integer receiverType, Long receiverId, String type, String title, String content, Long bizId) {
        createAndSendNotice(receiverType, receiverId, type, title, content, bizId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAndSendNotice(Integer receiverType, Long receiverId, String type, String title, String content, Long bizId, java.util.Map<String, Object> extraPayload) {
        SysNotice notice = new SysNotice();
        notice.setReceiverType(receiverType);
        notice.setReceiverId(receiverId != null ? receiverId : 0L);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setBizId(bizId);
        notice.setIsRead(0);
        notice.setCreateTime(LocalDateTime.now());
        this.save(notice);

        // 触发 WebSocket 实时推送
        try {
            JSONObject msg = new JSONObject();
            if (extraPayload != null && !extraPayload.isEmpty()) {
                msg.putAll(extraPayload);
            }
            msg.put("id", notice.getId());
            msg.put("type", type);
            msg.put("title", title);
            msg.put("content", content);
            msg.put("bizId", bizId);
            msg.put("createTime", notice.getCreateTime());

            String targetType = (receiverType != null && receiverType == 1) ? "admin" : "student";
            if (receiverId == null || receiverId == 0L) {
                WebSocketServer.broadcastToUserType(targetType, msg.toJSONString());
            } else {
                WebSocketServer.sendInfo(targetType, receiverId.toString(), msg.toJSONString());
            }
        } catch (Exception e) {
            log.warn("WebSocket 推送系统通知失败: {}", e.getMessage());
        }
    }

    private String resolveTypeName(String type) {
        if (type == null) {
            return "系统通知";
        }
        switch (type) {
            case "new_activity":
                return "新活动发布";
            case "gift_exchange":
                return "礼品兑换";
            case "gift_send":
                return "礼品发放";
            case "activity_card":
            case "activity_checkin_reward":
                return "打卡奖励";
            case "points_adjusted":
                return "积分调账";
            case "waitlist_promoted":
                return "候补递补";
            default:
                return "系统通知";
        }
    }

    private UserIdentity getCurrentUserIdentity() {
        if (AuthUtil.isStudent()) {
            return new UserIdentity(2, AuthUtil.requireStudentId());
        } else if (AuthUtil.isAdmin()) {
            return new UserIdentity(1, AuthUtil.requireAdminId());
        }

        // 兜底基于 Session 判断
        if (StpUtil.isLogin()) {
            Object userType = StpUtil.getSession().get("userType");
            if ("STUDENT".equals(userType)) {
                Object studentId = StpUtil.getSession().get("studentId");
                if (studentId != null) {
                    return new UserIdentity(2, Long.valueOf(studentId.toString()));
                }
            }
            Object userId = StpUtil.getSession().get("userId");
            if (userId != null) {
                return new UserIdentity(1, Long.valueOf(userId.toString()));
            }
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "未获取到有效登录身份");
    }

    private static class UserIdentity {
        final Integer receiverType; // 1-admin, 2-student
        final Long receiverId;

        UserIdentity(Integer receiverType, Long receiverId) {
            this.receiverType = receiverType;
            this.receiverId = receiverId;
        }
    }
}
