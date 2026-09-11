package com.campus.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.websocket.WebSocketServer;
import com.campus.modules.system.dto.NoticePageQuery;
import com.campus.modules.system.dto.SysNoticeVO;
import com.campus.modules.system.entity.SysNotice;
import com.campus.modules.system.mapper.SysNoticeMapper;
import com.campus.modules.system.service.SysNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SysNotice 系统通知服务实现类
 */
@Slf4j
@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    @Override
    public IPage<SysNoticeVO> getMyNotices(NoticePageQuery query) {
        UserIdentity identity = getCurrentUserIdentity();
        Page<SysNotice> page = new Page<>(query.getPage(), query.getSize());

        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getReceiverType, identity.receiverType)
                .and(w -> w.eq(SysNotice::getReceiverId, identity.receiverId).or().eq(SysNotice::getReceiverId, 0L))
                .eq(query.getIsRead() != null, SysNotice::getIsRead, query.getIsRead())
                .and(StringUtils.hasText(query.getKeyword()), w ->
                        w.like(SysNotice::getTitle, query.getKeyword())
                                .or()
                                .like(SysNotice::getContent, query.getKeyword())
                )
                .orderByDesc(SysNotice::getCreateTime);

        this.page(page, wrapper);

        Page<SysNoticeVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<SysNoticeVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Long getMyUnreadCount() {
        UserIdentity identity = getCurrentUserIdentity();
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getReceiverType, identity.receiverType)
                .and(w -> w.eq(SysNotice::getReceiverId, identity.receiverId).or().eq(SysNotice::getReceiverId, 0L))
                .eq(SysNotice::getIsRead, 0);
        return this.count(wrapper);
    }

    @Override
    public boolean markAsRead(Long id) {
        UserIdentity identity = getCurrentUserIdentity();
        SysNotice notice = this.getById(id);
        if (notice == null || !notice.getReceiverType().equals(identity.receiverType)) {
            return false;
        }
        if (notice.getReceiverId() != 0L && !notice.getReceiverId().equals(identity.receiverId)) {
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
    public boolean markAllAsRead() {
        UserIdentity identity = getCurrentUserIdentity();
        LambdaUpdateWrapper<SysNotice> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysNotice::getReceiverType, identity.receiverType)
                .and(w -> w.eq(SysNotice::getReceiverId, identity.receiverId).or().eq(SysNotice::getReceiverId, 0L))
                .eq(SysNotice::getIsRead, 0)
                .set(SysNotice::getIsRead, 1)
                .set(SysNotice::getReadTime, LocalDateTime.now());
        return this.update(updateWrapper);
    }

    @Override
    public boolean deleteNotice(Long id) {
        UserIdentity identity = getCurrentUserIdentity();
        SysNotice notice = this.getById(id);
        if (notice == null || !notice.getReceiverType().equals(identity.receiverType)) {
            return false;
        }
        if (notice.getReceiverId() != 0L && !notice.getReceiverId().equals(identity.receiverId)) {
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

    private SysNoticeVO convertToVO(SysNotice notice) {
        return SysNoticeVO.builder()
                .id(notice.getId())
                .receiverType(notice.getReceiverType())
                .receiverId(notice.getReceiverId())
                .type(notice.getType())
                .typeName(resolveTypeName(notice.getType()))
                .title(notice.getTitle())
                .content(notice.getContent())
                .bizId(notice.getBizId())
                .isRead(notice.getIsRead())
                .readTime(notice.getReadTime())
                .createTime(notice.getCreateTime())
                .build();
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
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.startsWith("student:")) {
            Long id = Long.parseLong(loginId.substring("student:".length()));
            return new UserIdentity(2, id);
        } else if (loginId.startsWith("admin:")) {
            Long id = Long.parseLong(loginId.substring("admin:".length()));
            return new UserIdentity(1, id);
        }

        // 兜底基于 Session 判断
        Object userType = StpUtil.getSession().get("userType");
        if ("STUDENT".equals(userType)) {
            Object studentId = StpUtil.getSession().get("studentId");
            return new UserIdentity(2, Long.valueOf(studentId.toString()));
        }
        Object userId = StpUtil.getSession().get("userId");
        return new UserIdentity(1, Long.valueOf(userId.toString()));
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
