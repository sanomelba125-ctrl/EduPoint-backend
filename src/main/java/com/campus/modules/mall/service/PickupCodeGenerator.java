package com.campus.modules.mall.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 礼品订单号与提货码生成组件
 *
 * <p><b>编码空间必须按峰值单量设计</b>：原提货码为 {@code PK + MMdd + 4 位随机}，
 * 每天只有 1 万个可用编码。单日订单量到几千时，碰撞把可用空间压到几乎为零，
 * 生成器连续 5 次撞码后走兜底分支，兜底分支之间仍会撞唯一键——
 * 2026-10-28 压测（消费并发=4）期间实测 205 笔订单因 {@code uk_pickup_code} 冲突回滚进死信。
 * 现改为 6 位随机（每天 100 万个编码），兜底分支换成 12 位十六进制（空间 2^48）。
 *
 * <p><b>订单号同理</b>：原实现是「毫秒时间戳 + 4 位随机」，Windows 上
 * {@code System.currentTimeMillis()} 的实际推进粒度约 15ms，同一时间片内并发下单会撞
 * {@code uk_order_no}。现把 4 位随机拆成「2 位进程内自增 + 2 位随机」，
 * 单实例内同毫秒并发不可能重复，多实例之间仍有 2 位随机隔离。
 */
@Slf4j
@Component
public class PickupCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter PICKUP_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMdd");

    /** 进程内订单号自增位（0-99），消除同毫秒并发撞号 */
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(SECURE_RANDOM.nextInt(100));
    /** 提货码随机位上限：6 位十进制，每天 100 万个编码 */
    private static final int PICKUP_RANDOM_BOUND = 1_000_000;

    /**
     * 生成高并发毫秒级全局唯一业务订单号
     * 规则：ORD + yyyyMMddHHmmssSSS(精确至毫秒) + 2 位进程内自增 + 2 位加密安全随机数（总长不变）
     */
    public String generateOrderNo() {
        int seq = Math.floorMod(ORDER_SEQ.getAndIncrement(), 100);
        int random = SECURE_RANDOM.nextInt(100);
        return "ORD" + ORDER_NO_FORMATTER.format(LocalDateTime.now())
                + String.format("%02d%02d", seq, random);
    }

    /**
     * 生成安全、防碰撞且语义清晰的礼品提货码
     * 规则：前缀 PK + 月日(MMdd, 4位) + 6 位加密安全随机数字（例如 PK1028834921，共 12 位）
     *
     * <p>注意：{@code isTaken} 只是一次「已提交数据」的存在性快照查询。
     * 在 REPEATABLE READ 事务里它看不到并发事务尚未提交的提货码，
     * 所以唯一索引 {@code uk_pickup_code} 才是最终防线，调用方需要能处理
     * {@code DuplicateKeyException} 并换码重试。
     *
     * @param isTaken 判定当前生成的提货码是否已存在的函数（为 true 表示已占用，需重试）
     * @return 全局唯一的提货码
     */
    public String generateUniquePickupCode(Predicate<String> isTaken) {
        String datePart = PICKUP_DATE_FORMATTER.format(LocalDate.now());
        for (int i = 0; i < 5; i++) {
            String candidate = "PK" + datePart + String.format("%06d", SECURE_RANDOM.nextInt(PICKUP_RANDOM_BOUND));
            if (isTaken == null || !isTaken.test(candidate)) {
                return candidate;
            }
            log.warn("提货码生成命中已占用编码，自动重新生成: candidate={}, retryCount={}", candidate, i + 1);
        }
        // 极端兜底（连续 5 次命中已占用）：12 位十六进制随机，空间 2^48，实际不可能再撞
        String fallback = "PK" + datePart
                + String.format("%012X", SECURE_RANDOM.nextLong() & 0xFFFFFFFFFFFFL);
        log.warn("提货码连续 5 次命中已占用，使用高熵兜底编码: {}", fallback);
        return fallback;
    }
}
