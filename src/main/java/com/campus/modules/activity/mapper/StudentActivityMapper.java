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
     * 单条 SQL 聚合所有活动的报名人数与打卡人数（避免导出报表时产生 2N 次循环查询）
     */
    @Select("SELECT activity_id AS activityId, " +
            "  COALESCE(SUM(CASE WHEN status IN (1, 2) THEN 1 ELSE 0 END), 0) AS enrolledCount, " +
            "  COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS checkinCount " +
            "FROM student_activity WHERE deleted = 0 GROUP BY activity_id")
    java.util.List<com.campus.modules.activity.dto.ActivityCountRow> countByActivity();
}
