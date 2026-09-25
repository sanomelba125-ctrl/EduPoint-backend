package com.campus.modules.student.strategy;

import java.io.Serializable;

/**
 * 积分变动计算上下文
 * 采用 Java 17 record 声明强类型不可变契约
 */
public record PointContext(
        Long studentId,
        Integer currentPoints,
        Integer change,
        String bizNo,
        Long refId,
        Long adminId,
        String reason
) implements Serializable {}
