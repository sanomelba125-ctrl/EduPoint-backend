package com.campus.modules.activity.dto;

import com.campus.modules.activity.entity.Activity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校园活动统一视图对象 (VO)
 * 对应 P5-5: 彻底隔离底层持久化实体，屏蔽 version, deleted, createdBy, updatedBy 等敏感内部字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityVO {

    private Long id;
    private String title;
    private String category;
    private String categoryName;
    private String description;
    private String ruleDescription;
    private String coverImage;
    private Integer points;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer registrationLimit;
    private Integer participantCount;
    private Integer waitlistEnabled;
    private String locationName;
    private String locationAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer locationRadius;
    private Integer locationCheckEnabled;
    private Integer certificateEnabled;
    private String certificateTitle;
    private String certificateIssuer;
    private Integer status;
    private LocalDateTime createTime;

    /**
     * 当前登录学生的动态报名状态（NONE / ENROLLED / WAITLIST / COMPLETED）
     */
    private String myEnrollmentStatus;

    /**
     * 当前登录学生的候补排队序号
     */
    private Integer myQueueNumber;

    /**
     * 静态实体转换工厂方法
     * @param a 活动实体对象
     * @return 转换后的活动视图对象
     */
    public static ActivityVO fromEntity(Activity a) {
        if (a == null) {
            return null;
        }
        return ActivityVO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .category(a.getCategory())
                .categoryName(toCategoryName(a.getCategory()))
                .description(a.getDescription())
                .ruleDescription(a.getRuleDescription())
                .coverImage(a.getCoverImage())
                .points(a.getPoints())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .registrationLimit(a.getRegistrationLimit())
                .participantCount(a.getParticipantCount())
                .waitlistEnabled(a.getWaitlistEnabled())
                .locationName(a.getLocationName())
                .locationAddress(a.getLocationAddress())
                .longitude(a.getLongitude())
                .latitude(a.getLatitude())
                .locationRadius(a.getLocationRadius())
                .locationCheckEnabled(a.getLocationCheckEnabled())
                .certificateEnabled(a.getCertificateEnabled())
                .certificateTitle(a.getCertificateTitle())
                .certificateIssuer(a.getCertificateIssuer())
                .status(a.getStatus())
                .createTime(a.getCreateTime())
                .myEnrollmentStatus(a.getMyEnrollmentStatus())
                .myQueueNumber(a.getMyQueueNumber())
                .build();
    }

    private static String toCategoryName(String category) {
        if (category == null || category.isBlank()) {
            return "其他活动";
        }
        return switch (category.trim().toUpperCase()) {
            case "ACADEMIC" -> "学术讲座";
            case "VOLUNTEER" -> "志愿服务";
            case "SPORTS" -> "文体竞技";
            case "PRACTICE" -> "社会实践";
            default -> "其他活动";
        };
    }
}
