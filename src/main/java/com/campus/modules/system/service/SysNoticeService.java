package com.campus.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.system.dto.NoticePageQuery;
import com.campus.modules.system.dto.SysNoticeVO;
import com.campus.modules.system.entity.SysNotice;

/**
 * 系统通知服务接口
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 分页查询当前用户的通知列表
     */
    IPage<SysNoticeVO> getMyNotices(NoticePageQuery query);

    /**
     * 查询当前用户未读通知数量
     */
    Long getMyUnreadCount();

    /**
     * 标记单条通知为已读
     */
    boolean markAsRead(Long id);

    /**
     * 一键标记全部未读通知为已读
     */
    boolean markAllAsRead();

    /**
     * 删除单条通知 (逻辑删除)
     */
    boolean deleteNotice(Long id);

    /**
     * 业务统一保存通知到数据库并触发 WebSocket 实时推送
     * @param receiverType 接收方类型: 1-管理员 2-学生
     * @param receiverId 接收人ID (0代表全员广播)
     * @param type 通知类型 (如 new_activity, gift_exchange, activity_card, gift_send, points_adjusted, waitlist_promoted)
     * @param title 标题
     * @param content 正文
     * @param bizId 关联业务ID
     */
    void createAndSendNotice(Integer receiverType, Long receiverId, String type, String title, String content, Long bizId);

    /**
     * 业务统一保存通知并附加自定义数据触发 WebSocket 实时推送
     */
    void createAndSendNotice(Integer receiverType, Long receiverId, String type, String title, String content, Long bizId, java.util.Map<String, Object> extraPayload);
}
