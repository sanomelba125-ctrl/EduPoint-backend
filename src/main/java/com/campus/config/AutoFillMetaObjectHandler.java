package com.campus.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充处理器
 * 对应 P4-5: 自动填充 create_time, update_time, created_by, updated_by
 * 解决底层直接依赖 DDL 默认值导致实体 insert 后内存字段为 null 的问题
 */
@Slf4j
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        if (metaObject.hasSetter("createTime") && getFieldValByName("createTime", metaObject) == null) {
            setFieldValByName("createTime", now, metaObject);
        }
        if (metaObject.hasSetter("updateTime") && getFieldValByName("updateTime", metaObject) == null) {
            setFieldValByName("updateTime", now, metaObject);
        }

        Long currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            if (metaObject.hasSetter("createdBy") && getFieldValByName("createdBy", metaObject) == null) {
                setFieldValByName("createdBy", currentUserId, metaObject);
            }
            if (metaObject.hasSetter("updatedBy") && getFieldValByName("updatedBy", metaObject) == null) {
                setFieldValByName("updatedBy", currentUserId, metaObject);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        if (metaObject.hasSetter("updateTime")) {
            setFieldValByName("updateTime", now, metaObject);
        }
        Long currentUserId = getCurrentUserId();
        if (currentUserId != null && metaObject.hasSetter("updatedBy")) {
            setFieldValByName("updatedBy", currentUserId, metaObject);
        }
    }

    private Long getCurrentUserId() {
        try {
            if (StpUtil.isLogin()) {
                String loginIdStr = StpUtil.getLoginIdAsString();
                if (loginIdStr != null) {
                    if (loginIdStr.startsWith("admin_")) {
                        return Long.parseLong(loginIdStr.substring(6));
                    } else if (loginIdStr.startsWith("student_")) {
                        return Long.parseLong(loginIdStr.substring(8));
                    }
                    return Long.parseLong(loginIdStr);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
