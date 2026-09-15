package com.campus;

import com.campus.common.constant.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户权限角色常量单元测试
 */
class UserRoleTest {

    @Test
    @DisplayName("测试角色常量标识严格遵循三权分立安全规范")
    void testRoleConstants() {
        assertEquals("SUPER_ADMIN", UserRole.SUPER_ADMIN);
        assertEquals("ADMIN", UserRole.ADMIN);
        assertEquals("COUNSELOR", UserRole.COUNSELOR);
        assertEquals("STUDENT", UserRole.STUDENT);
    }
}
