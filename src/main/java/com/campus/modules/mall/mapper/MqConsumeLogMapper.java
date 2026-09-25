package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消费幂等表数据访问层 (P1-4)
 */
@Mapper
public interface MqConsumeLogMapper extends BaseMapper<MqConsumeLog> {

    /**
     * 判断是否已消费成功
     */
    @Select("SELECT COUNT(1) FROM mq_consume_log WHERE message_id = #{messageId} AND consumer_name = #{consumerName}")
    int existsConsumed(@Param("messageId") String messageId, @Param("consumerName") String consumerName);

    /**
     * 原子记录消费日志 (依靠 uk_msg_consumer 唯一索引防并发穿透)
     */
    @Insert("INSERT INTO mq_consume_log (message_id, consumer_name, biz_type, biz_id, status, consume_time) " +
            "VALUES (#{messageId}, #{consumerName}, #{bizType}, #{bizId}, #{status}, NOW())")
    int insertConsumeLog(@Param("messageId") String messageId,
                         @Param("consumerName") String consumerName,
                         @Param("bizType") String bizType,
                         @Param("bizId") String bizId,
                         @Param("status") int status);
}
