package com.campus.modules.admin;

import com.campus.common.result.ResultCode;
import com.campus.common.exception.BusinessException;
import com.campus.modules.admin.entity.SysUser;
import com.campus.modules.admin.mapper.SysUserMapper;
import com.campus.modules.admin.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 管理员账号核心安全与内置超管防御测试
 */
class SysUserSuperAdminProtectionTest {

    private SysUserMapper sysUserMapper;
    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        sysUserService = new SysUserServiceImpl();
        ReflectionTestUtils.setField(sysUserService, "baseMapper", sysUserMapper);
    }

    @Test
    @DisplayName("测试重置密码时：目标管理员不存在应抛出 USER_NOT_EXIST 异常")
    void testResetPassword_UserNotExists() {
        when(sysUserMapper.selectById(any(Serializable.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            sysUserService.resetPassword(999L, "newPassword123");
        });

        assertEquals(ResultCode.USER_NOT_EXIST.getCode(), ex.getCode());
        assertEquals("目标管理员不存在", ex.getMessage());
    }

    @Test
    @DisplayName("测试重置密码时：禁止重置内建超级管理员 (ID=1) 的密码")
    void testResetPassword_BuiltinAdminById_Forbidden() {
        SysUser superAdmin = new SysUser();
        superAdmin.setId(1L);
        superAdmin.setUsername("super_root");
        when(sysUserMapper.selectById(1L)).thenReturn(superAdmin);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            sysUserService.resetPassword(1L, "newPassword123");
        });

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("禁止重置系统内置超级管理员账号密码", ex.getMessage());
    }

    @Test
    @DisplayName("测试重置密码时：禁止重置内建超级管理员 (username=admin) 的密码")
    void testResetPassword_BuiltinAdminByUsername_Forbidden() {
        SysUser adminUser = new SysUser();
        adminUser.setId(88L);
        adminUser.setUsername("admin");
        when(sysUserMapper.selectById(88L)).thenReturn(adminUser);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            sysUserService.resetPassword(88L, "newPassword123");
        });

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("禁止重置系统内置超级管理员账号密码", ex.getMessage());
    }

    @Test
    @DisplayName("测试删除管理员时：禁止删除内建超管")
    void testDeleteUser_SuperAdmin_Forbidden() {
        SysUser adminUser = new SysUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        when(sysUserMapper.selectById(1L)).thenReturn(adminUser);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            sysUserService.deleteUser(1L, 2L);
        });

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("禁止注销/删除系统超级管理员账号", ex.getMessage());
    }

    @Test
    @DisplayName("测试停用管理员时：禁止停用内建超管")
    void testUpdateUserStatus_SuperAdmin_Forbidden() {
        SysUser adminUser = new SysUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        when(sysUserMapper.selectById(1L)).thenReturn(adminUser);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            sysUserService.updateUserStatus(1L, 0);
        });

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("禁止禁用系统超级管理员账号", ex.getMessage());
    }
}
