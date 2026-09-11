package com.campus.modules.certificate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.modules.certificate.dto.CertificateAdminVO;
import com.campus.modules.certificate.dto.CertificateMyVO;
import com.campus.modules.certificate.dto.CertificateVerifyVO;
import com.campus.modules.certificate.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CertificateMapper extends BaseMapper<Certificate> {

    @Select("<script>SELECT c.id, c.certificate_no, c.student_id, s.name AS student_name, s.student_no, " +
            "c.activity_id, a.title AS activity_title, c.title, c.issuer, c.issue_time, c.file_url, c.status, " +
            "c.revoke_reason, c.revoked_by, c.revoked_time, c.verify_count " +
            "FROM certificate c JOIN student s ON s.id=c.student_id JOIN activity a ON a.id=c.activity_id " +
            "WHERE c.deleted=0 AND s.deleted=0 AND a.deleted=0 " +
            "<if test='certificateNo != null and certificateNo != \"\"'>AND c.certificate_no LIKE CONCAT('%',#{certificateNo},'%')</if> " +
            "<if test='studentName != null and studentName != \"\"'>AND s.name LIKE CONCAT('%',#{studentName},'%')</if> " +
            "<if test='studentNo != null and studentNo != \"\"'>AND s.student_no LIKE CONCAT('%',#{studentNo},'%')</if> " +
            "<if test='activityTitle != null and activityTitle != \"\"'>AND a.title LIKE CONCAT('%',#{activityTitle},'%')</if> " +
            "<if test='status != null'>AND c.status=#{status}</if> ORDER BY c.issue_time DESC</script>")
    IPage<CertificateAdminVO> selectAdminPage(Page<CertificateAdminVO> page,
                                                @Param("certificateNo") String certificateNo,
                                                @Param("studentName") String studentName,
                                                @Param("studentNo") String studentNo,
                                                @Param("activityTitle") String activityTitle,
                                                @Param("status") Integer status);

    @Select("SELECT c.id, c.certificate_no, a.title AS activity_title, c.title, c.issuer, c.issue_time, c.file_url, c.status " +
            "FROM certificate c JOIN activity a ON a.id=c.activity_id " +
            "WHERE c.deleted=0 AND c.student_id=#{studentId} ORDER BY c.issue_time DESC")
    List<CertificateMyVO> selectMyList(@Param("studentId") Long studentId);

    @Select("SELECT c.id, c.certificate_no, s.name AS student_name, a.title AS activity_title, c.title, c.issuer, " +
            "c.issue_time, c.file_url, c.status, c.revoke_reason " +
            "FROM certificate c JOIN student s ON s.id=c.student_id JOIN activity a ON a.id=c.activity_id " +
            "WHERE c.deleted=0 AND s.deleted=0 AND a.deleted=0 AND c.certificate_no=#{certificateNo} LIMIT 1")
    CertificateVerifyVO selectVerifyByNo(@Param("certificateNo") String certificateNo);

    @Update("UPDATE certificate SET verify_count=COALESCE(verify_count,0)+1 WHERE id=#{id} AND deleted=0")
    int incrementVerifyCount(@Param("id") Long id);
}
