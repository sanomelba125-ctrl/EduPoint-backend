package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.Gift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GiftMapper extends BaseMapper<Gift> {

    /**
     * 条件原子扣减库存：以「库存够」为唯一前提，由 InnoDB 行锁串行化并发扣减。
     *
     * <p><b>为什么不再用 version 做 CAS</b>：兑换主流程运行在 REPEATABLE READ 事务里，
     * 版本号冲突后重新 {@code selectById} 读到的仍是<b>本事务快照</b>中的旧 version，
     * 永远看不到其他消费者已提交的新版本，重试必然全部失败。
     * 单消费者时冲突几乎为零所以没暴露；消费并发调到 4 后实测 86% 的订单被误判为「库存不足」。
     *
     * <p>{@code stock >= quantity} 与 {@code UPDATE} 在同一条语句内由行锁保证原子，
     * 效果等价于乐观锁且不会误判；{@code version} 仍自增，供其它读路径感知变更。
     *
     * @return 受影响行数，0 表示库存不足或礼品不存在
     */
    @Update("UPDATE gift SET stock = stock - #{quantity}, version = version + 1 " +
            "WHERE id = #{id} AND stock >= #{quantity} AND deleted = 0")
    int deductStockIfEnough(@Param("id") Long id,
                            @Param("quantity") Integer quantity);

    /**
     * 取消订单时原子恢复库存
     */
    @Update("UPDATE gift SET stock = stock + #{quantity}, version = version + 1 " +
            "WHERE id = #{id} AND deleted = 0")
    int increaseStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 补货：入库总量与可用库存在数据库当前值上同步原子自增（只增不减）。
     * 不使用“读取快照 → 写回”的方式，避免与并发的兑换扣减互相覆盖。
     * update_time 由表定义的 ON UPDATE CURRENT_TIMESTAMP 自动刷新。
     *
     * @param quantity 本次入库件数，必须为正整数
     * @return 受影响行数，0 表示礼品不存在或已被删除
     */
    @Update("UPDATE gift SET stock = stock + #{quantity}, total_stock = total_stock + #{quantity}, " +
            "updated_by = #{adminId,jdbcType=BIGINT} " +
            "WHERE id = #{id} AND deleted = 0")
    int restock(@Param("id") Long id,
                @Param("quantity") Integer quantity,
                @Param("adminId") Long adminId);

    /**
     * 盘亏报损核减库存：可用库存与入库总量同步原子自减。
     * 使用条件 SQL 更新：WHERE stock >= #{quantity}，严防扣成负数。
     *
     * @param id 礼品ID
     * @param quantity 本次核减件数（正整数）
     * @param adminId 操作员ID
     * @return 受影响行数，0 表示库存不足或礼品不存在
     */
    @Update("UPDATE gift SET stock = stock - #{quantity}, total_stock = total_stock - #{quantity}, " +
            "updated_by = #{adminId,jdbcType=BIGINT} " +
            "WHERE id = #{id} AND stock >= #{quantity} AND deleted = 0")
    int deductStock(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("adminId") Long adminId);
}
