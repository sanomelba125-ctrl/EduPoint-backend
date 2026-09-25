package com.campus.modules.mall.service;

/**
 * 礼品与商城模块 Redis 统一键名规范
 */
public final class GiftRedisKeys {

    private GiftRedisKeys() {
    }

    /** 礼品详情 JSON 缓存键：gift:detail:{giftId} */
    public static String detail(Long id) {
        return "gift:detail:" + id;
    }

    /** 礼品详情重构互斥锁键：gift:lock:detail:{giftId} */
    public static String detailRebuildLock(Long id) {
        return "gift:lock:detail:" + id;
    }

    /** 礼品库存预扣键：gift:stock:{giftId} */
    public static String stock(Long id) {
        return "gift:stock:" + id;
    }

    /** 防重兑换令牌键：gift:exchange:token:{studentId}:{giftId} */
    public static String exchangeToken(Long studentId, Long giftId) {
        return "gift:exchange:token:" + studentId + ":" + giftId;
    }

    /** 并发防重占位键：gift:exchange:idem:{studentId}:{requestId} */
    public static String exchangeIdempotent(Long studentId, String requestId) {
        return "gift:exchange:idem:" + studentId + ":" + (requestId != null ? requestId.trim() : "");
    }
}
