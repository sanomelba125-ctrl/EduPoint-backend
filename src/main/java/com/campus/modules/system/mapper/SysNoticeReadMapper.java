package com.campus.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.system.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    /**
     * 标记单条广播通知为已读 (UPSERT: 存在则确保未删除并更新已读时间)
     */
    @Insert("INSERT INTO sys_notice_read (notice_id, user_id, user_type, is_deleted, read_time, create_time, update_time) " +
            "VALUES (#{noticeId}, #{userId}, #{userType}, 0, NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE is_deleted = 0, read_time = NOW(), update_time = NOW()")
    int upsertReadRecord(@Param("noticeId") Long noticeId,
                         @Param("userId") Long userId,
                         @Param("userType") Integer userType);

    /**
     * 标记广播通知为用户隐藏/软删除 (UPSERT: 存在则将 is_deleted 置 1)
     */
    @Insert("INSERT INTO sys_notice_read (notice_id, user_id, user_type, is_deleted, read_time, create_time, update_time) " +
            "VALUES (#{noticeId}, #{userId}, #{userType}, 1, NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE is_deleted = 1, update_time = NOW()")
    int upsertDeletedRecord(@Param("noticeId") Long noticeId,
                            @Param("userId") Long userId,
                            @Param("userType") Integer userType);

    /**
     * 一键已读当前用户所有未读的广播通知
     */
    @Insert("INSERT INTO sys_notice_read (notice_id, user_id, user_type, is_deleted, read_time, create_time, update_time) " +
            "SELECT n.id, #{userId}, #{userType}, 0, NOW(), NOW(), NOW() " +
            "FROM sys_notice n " +
            "LEFT JOIN sys_notice_read r ON n.id = r.notice_id AND r.user_id = #{userId} AND r.user_type = #{userType} " +
            "WHERE n.deleted = 0 " +
            "  AND n.receiver_type = #{userType} " +
            "  AND n.receiver_id = 0 " +
            "  AND r.id IS NULL " +
            "ON DUPLICATE KEY UPDATE is_deleted = 0, read_time = NOW(), update_time = NOW()")
    int markAllBroadcastAsRead(@Param("userId") Long userId,
                               @Param("userType") Integer userType);
}
