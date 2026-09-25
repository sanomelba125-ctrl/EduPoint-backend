package com.campus.modules.student.strategy;

import java.io.Serializable;

/**
 * 积分策略执行结果
 * 采用 Java 17 record 声明强类型不可变契约
 */
public record PointCalculationResult(
        int pointChange,
        int beforePoints,
        int afterPoints,
        boolean addTotalPoints,
        String remark,
        String bizNo
) implements Serializable {}
