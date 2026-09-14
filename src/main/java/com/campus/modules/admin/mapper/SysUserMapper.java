package com.campus.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 忽略逻辑删除，根据用户名检索系统用户（用于新增查重，防止碰撞 uk_username 唯一索引）
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsernameIgnoreDeleted(@Param("username") String username);
}
