package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.Gift;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GiftMapper extends BaseMapper<Gift> {

    /**
     * 乐观锁与库存下限双重原子扣减
     */
    @Update("UPDATE gift SET stock = stock - #{quantity}, version = version + 1 " +
            "WHERE id = #{id} AND stock >= #{quantity} AND version = #{version} AND deleted = 0")
    int reduceStockWithOptimisticLock(@Param("id") Long id,
                                      @Param("quantity") Integer quantity,
                                      @Param("version") Integer version);

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
