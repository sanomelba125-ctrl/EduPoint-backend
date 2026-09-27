package com.campus.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.modules.system.dto.NoticePageQuery;
import com.campus.modules.system.dto.SysNoticeVO;
import com.campus.modules.system.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 读写分离多租户视角：分页查询当前用户的个人通知与未隐藏的公共广播
     */
    @Select("<script>" +
            "SELECT " +
            "  n.id, " +
            "  n.receiver_type, " +
            "  n.receiver_id, " +
            "  n.type, " +
            "  n.title, " +
            "  n.content, " +
            "  n.biz_id, " +
            "  n.create_time, " +
            "  CASE " +
            "    WHEN n.receiver_id = 0 THEN CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END " +
            "    ELSE n.is_read " +
            "  END AS is_read, " +
            "  CASE " +
            "    WHEN n.receiver_id = 0 THEN r.read_time " +
            "    ELSE n.read_time " +
            "  END AS read_time " +
            "FROM sys_notice n " +
            "LEFT JOIN sys_notice_read r " +
            "  ON n.id = r.notice_id " +
            "  AND r.user_id = #{userId} " +
            "  AND r.user_type = #{userType} " +
            "WHERE n.deleted = 0 " +
            "  AND n.receiver_type = #{userType} " +
            "  AND ( " +
            "    (n.receiver_id = #{userId}) " +
            "    OR " +
            "    (n.receiver_id = 0 AND (r.is_deleted IS NULL OR r.is_deleted = 0)) " +
            "  ) " +
            "  <if test='query.isRead != null'> " +
            "    <choose> " +
            "      <when test='query.isRead == 1'> " +
            "        AND ( " +
            "          (n.receiver_id = #{userId} AND n.is_read = 1) " +
            "          OR " +
            "          (n.receiver_id = 0 AND r.id IS NOT NULL AND (r.is_deleted IS NULL OR r.is_deleted = 0)) " +
            "        ) " +
            "      </when> " +
            "      <otherwise> " +
            "        AND ( " +
            "          (n.receiver_id = #{userId} AND n.is_read = 0) " +
            "          OR " +
            "          (n.receiver_id = 0 AND r.id IS NULL) " +
            "        ) " +
            "      </otherwise> " +
            "    </choose> " +
            "  </if> " +
            "  <if test='query.keyword != null and query.keyword != \"\"'> " +
            "    AND (n.title LIKE CONCAT('%', #{query.keyword}, '%') OR n.content LIKE CONCAT('%', #{query.keyword}, '%')) " +
            "  </if> " +
            "ORDER BY n.create_time DESC" +
            "</script>")
    IPage<SysNoticeVO> selectUserNoticePage(Page<SysNoticeVO> page,
                                           @Param("userId") Long userId,
                                           @Param("userType") Integer userType,
                                           @Param("query") NoticePageQuery query);

    /**
     * 读写分离多租户视角：统计当前用户的未读通知数（含个人私信与未读广播）
     */
    @Select("<script>" +
            "SELECT COUNT(1) " +
            "FROM sys_notice n " +
            "LEFT JOIN sys_notice_read r " +
            "  ON n.id = r.notice_id " +
            "  AND r.user_id = #{userId} " +
            "  AND r.user_type = #{userType} " +
            "WHERE n.deleted = 0 " +
            "  AND n.receiver_type = #{userType} " +
            "  AND ( " +
            "    (n.receiver_id = #{userId} AND n.is_read = 0) " +
            "    OR " +
            "    (n.receiver_id = 0 AND r.id IS NULL) " +
            "  )" +
            "</script>")
    Long countUserUnread(@Param("userId") Long userId,
                         @Param("userType") Integer userType);
}
