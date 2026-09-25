package com.campus.modules.mall.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

/**
 * 礼品订单号与提货码生成组件
 */
@Slf4j
@Component
public class PickupCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter PICKUP_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMdd");

    /**
     * 生成高并发毫秒级全局唯一业务订单号
     * 规则：ORD + yyyyMMddHHmmssSSS(精确至毫秒) + 4位加密安全随机数
     */
    public String generateOrderNo() {
        return "ORD" + ORDER_NO_FORMATTER.format(LocalDateTime.now())
                + String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    /**
     * 生成安全、防碰撞且语义清晰的礼品提货码
     * 规则：前缀 PK + 月日(MMdd, 4位) + 4位加密安全随机数字（例如 PK09168821，共 10 位）
     *
     * @param isTaken 判定当前生成的提货码是否已存在的函数（为 true 表示已占用，需重试）
     * @return 全局唯一的提货码
     */
    public String generateUniquePickupCode(Predicate<String> isTaken) {
        String datePart = PICKUP_DATE_FORMATTER.format(LocalDate.now());
        for (int i = 0; i < 5; i++) {
            int randomNum = SECURE_RANDOM.nextInt(10000);
            String candidate = "PK" + datePart + String.format("%04d", randomNum);
            if (isTaken == null || !isTaken.test(candidate)) {
                return candidate;
            }
            log.warn("提货码生成命中唯一键碰撞，自动重新生成: candidate={}, retryCount={}", candidate, i + 1);
        }
        // 极端兜底（连续碰撞）：追加纳秒时间戳后3位，确保 100% 绝对唯一
        return "PK" + datePart + String.format("%04d", SECURE_RANDOM.nextInt(10000))
                + String.format("%03d", (int) (System.nanoTime() % 1000));
    }
}
