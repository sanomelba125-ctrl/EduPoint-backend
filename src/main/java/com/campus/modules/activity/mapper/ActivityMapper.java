package com.campus.modules.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.activity.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Update("UPDATE activity SET participant_count = COALESCE(participant_count,0) + 1, version = version + 1 WHERE id=#{id} AND version=#{version} AND deleted=0 AND status IN (0,1) AND (registration_limit=0 OR participant_count < registration_limit)")
    int incrementParticipant(@Param("id") Long id, @Param("version") Integer version);

    @Update("UPDATE activity SET participant_count = GREATEST(COALESCE(participant_count,0) - 1, 0), version = version + 1 WHERE id=#{id} AND deleted=0")
    int decrementParticipant(@Param("id") Long id);

    /**
     * 聚合统计各分类活动数量
     */
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(category, 'OTHER') AS category, COUNT(*) AS cnt FROM activity WHERE deleted = 0 GROUP BY category")
    java.util.List<java.util.Map<String, Object>> countGroupByCategory();
}
