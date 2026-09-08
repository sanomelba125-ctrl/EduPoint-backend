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
}
