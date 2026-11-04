package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.entity.StudentActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentActivityMapper extends BaseMapper<StudentActivity> {
    @Select("<script>SELECT sa.*, s.name AS student_name, s.student_no AS student_no, a.title AS activity_title " +
            "FROM student_activity sa JOIN student s ON s.id=sa.student_id JOIN activity a ON a.id=sa.activity_id " +
            "WHERE sa.deleted=0 AND s.deleted=0 AND a.deleted=0 " +
            "<if test='studentName != null and studentName != \"\"'>AND s.name LIKE CONCAT('%', #{studentName}, '%') </if>" +
            "<if test='studentNo != null and studentNo != \"\"'>AND s.student_no LIKE CONCAT('%', #{studentNo}, '%') </if>" +
            "<if test='activityTitle != null and activityTitle != \"\"'>AND a.title LIKE CONCAT('%', #{activityTitle}, '%') </if>" +
            "<if test='status != null'>AND sa.status=#{status} </if> ORDER BY sa.signup_time DESC</script>")
    IPage<ActivityRecordVO> selectAdminPage(Page<ActivityRecordVO> page, @Param("studentName") String studentName,
                                              @Param("studentNo") String studentNo, @Param("activityTitle") String activityTitle,
                                              @Param("status") Integer status);

    /**
     * 报表导出专用列表查询（带 LIMIT 封顶，不经过 IPage 分页插件，杜绝 maxLimit(100L) 静默截断）
     */
    @Select("<script>SELECT sa.*, s.name AS student_name, s.student_no AS student_no, a.title AS activity_title " +
            "FROM student_activity sa JOIN student s ON s.id=sa.student_id JOIN activity a ON a.id=sa.activity_id " +
            "WHERE sa.deleted=0 AND s.deleted=0 AND a.deleted=0 " +
            "<if test='studentName != null and studentName != \"\"'>AND s.name LIKE CONCAT('%', #{studentName}, '%') </if>" +
            "<if test='studentNo != null and studentNo != \"\"'>AND s.student_no LIKE CONCAT('%', #{studentNo}, '%') </if>" +
            "<if test='activityTitle != null and activityTitle != \"\"'>AND a.title LIKE CONCAT('%', #{activityTitle}, '%') </if>" +
            "<if test='status != null'>AND sa.status=#{status} </if> " +
            "ORDER BY sa.signup_time DESC " +
            "LIMIT #{limit}</script>")
    java.util.List<ActivityRecordVO> selectExportList(@Param("studentName") String studentName,
                                                     @Param("studentNo") String studentNo,
                                                     @Param("activityTitle") String activityTitle,
                                                     @Param("status") Integer status,
                                                     @Param("limit") int limit);

    /**
     * 单条 SQL 聚合所有活动的报名人数与打卡人数（避免导出报表时产生 2N 次循环查询）
     */
    @Select("SELECT activity_id AS activityId, " +
            "  COALESCE(SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END), 0) AS enrolledCount, " +
            "  COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS checkinCount " +
            "FROM student_activity WHERE deleted = 0 GROUP BY activity_id")
    java.util.List<com.campus.modules.activity.dto.ActivityCountRow> countByActivity();

    /**
     * 全局打卡参与记录指标统计（SQL下推聚合，避免分页前端口径失真）
     */
    @Select("SELECT " +
            "  COUNT(*) AS totalRecords, " +
            "  COALESCE(SUM(CASE WHEN sa.status = 2 THEN 1 ELSE 0 END), 0) AS completedCount, " +
            "  COALESCE(SUM(CASE WHEN sa.status = 1 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "  COALESCE(SUM(CASE WHEN sa.status = 3 THEN 1 ELSE 0 END), 0) AS cancelledCount, " +
            "  COALESCE(SUM(CASE WHEN sa.status = 2 THEN sa.points_awarded ELSE 0 END), 0) AS pointsDistributed " +
            "FROM student_activity sa " +
            "JOIN student s ON s.id = sa.student_id AND s.deleted = 0 " +
            "JOIN activity a ON a.id = sa.activity_id AND a.deleted = 0 " +
            "WHERE sa.deleted = 0")
    com.campus.modules.activity.dto.ActivityRecordMetricsVO selectRecordMetrics();

    /**
     * P2-11: 查询学生已有效报名且未结束的活动列表（用于时间冲突重叠检测）
     */
    @Select("SELECT a.* FROM student_activity sa " +
            "JOIN activity a ON sa.activity_id = a.id " +
            "WHERE sa.student_id = #{studentId} " +
            "  AND sa.status IN (1, 2) " +
            "  AND sa.deleted = 0 " +
            "  AND a.deleted = 0 " +
            "  AND a.status != 2 " +
            "  AND a.id != #{excludeActivityId}")
    java.util.List<com.campus.modules.activity.entity.Activity> selectActiveEnrolledActivities(@Param("studentId") Long studentId,
                                                                                                @Param("excludeActivityId") Long excludeActivityId);
}
