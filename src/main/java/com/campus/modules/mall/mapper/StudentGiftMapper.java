package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.StudentGift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface StudentGiftMapper extends BaseMapper<StudentGift> {

    /**
     * 全局礼品兑换订单指标统计（SQL下推聚合，避免分页前端口径失真）
     */
    @Select("SELECT " +
            "  COUNT(*) AS totalCount, " +
            "  COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "  COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS completedCount, " +
            "  COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS cancelledCount, " +
            "  COALESCE(SUM(CASE WHEN status != 3 THEN points_cost ELSE 0 END), 0) AS totalPoints " +
            "FROM student_gift WHERE deleted = 0")
    com.campus.modules.mall.dto.GiftOrderMetricsVO selectOrderMetrics();

    /**
     * 状态守卫的取消落库：仅当订单仍处于期望状态时才写入取消态。
     * 用于替代「先查后改」的订单取消流程，保证并发下只有一方能把 status 从期望值迁走。
     *
     * @return 受影响行数；0 表示订单已被其他并发操作抢先流转，调用方必须放弃全部副作用（退积分/退库存/发通知）
     */
    @Update("UPDATE student_gift SET status = 3, refund_points_status = #{refundPointsStatus}, "
            + "cancel_time = #{cancelTime,jdbcType=TIMESTAMP}, cancel_reason = #{cancelReason,jdbcType=VARCHAR}, "
            + "operator_id = #{operatorId,jdbcType=BIGINT} "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND deleted = 0")
    int cancelOrderWithStatusGuard(@Param("id") Long id,
                                   @Param("expectedStatus") Integer expectedStatus,
                                   @Param("refundPointsStatus") Integer refundPointsStatus,
                                   @Param("cancelTime") LocalDateTime cancelTime,
                                   @Param("cancelReason") String cancelReason,
                                   @Param("operatorId") Long operatorId);

    /**
     * 状态守卫的核销落库：仅当订单仍处于期望状态时才写入已核销态。
     * remark 传 null 时保留数据库原值（等价于原实现的「有内容才覆盖」）。
     *
     * @return 受影响行数；0 表示订单已被其他并发操作抢先流转，调用方不得再发核销通知
     */
    @Update("UPDATE student_gift SET status = 2, delivery_time = #{deliveryTime,jdbcType=TIMESTAMP}, "
            + "operator_id = #{operatorId,jdbcType=BIGINT}, remark = COALESCE(#{remark,jdbcType=VARCHAR}, remark) "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND deleted = 0")
    int confirmDeliveryWithStatusGuard(@Param("id") Long id,
                                       @Param("expectedStatus") Integer expectedStatus,
                                       @Param("deliveryTime") LocalDateTime deliveryTime,
                                       @Param("operatorId") Long operatorId,
                                       @Param("remark") String remark);
}
