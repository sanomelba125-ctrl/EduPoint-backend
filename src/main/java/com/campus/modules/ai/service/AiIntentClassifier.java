package com.campus.modules.ai.service;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 对话的意图分流规则（2026-10-29 修复 P0-4）。
 *
 * <h3>为什么需要它</h3>
 * 平台有一条硬性隐私约束：<b>个人私有数据（积分、订单、提货码、证书等）绝不出域</b>，
 * 只能由本地 {@code ToolRegistry} 查询，不能交给外部大模型。于是对话入口必须先判断
 * 「这句话是不是在问我的个人数据」，再决定走本地工具还是走大模型。
 *
 * <p>修复前这条判断是 {@code question.contains("我")}，两个方向都会翻车：
 * <ul>
 *   <li>「我<b>怎么</b>参加活动拿积分」——含「我」，被判为个人查询 → 本地按「积分」关键字
 *       返回积分余额，答非所问；</li>
 *   <li>「查询积分流水」——不含「我」，被判为非个人查询 → 交给大模型，而个人工具
 *       {@code isPublic=false} 不会暴露给它，模型只能回一句
 *       「请查看上方查询结果」，而上方什么都没有。</li>
 * </ul>
 *
 * <h3>现在的判定</h3>
 * <pre>
 * 是个人查询 = 不是「怎么/如何/规则/流程」类咨询
 *            &amp;&amp; 不含「什么/哪些」这类开放疑问
 *            &amp;&amp; 命中个人业务对象（积分/订单/活动/证书…）
 *            &amp;&amp; （有「我的」等所有格 或 有「查/多少/余额」等查询动作 或 是极短的裸对象词）
 * </pre>
 *
 * <p>纯静态工具类，不依赖 Spring 上下文，便于用表驱动单测锁住行为。</p>
 */
public final class AiIntentClassifier {

    /** 本地工具能直接回答的业务意图；KNOWLEDGE 表示与个人数据无关，应走知识库/大模型 */
    public enum Intent {
        POINTS, ORDERS, ACTIVITIES, CERTIFICATES, KNOWLEDGE
    }

    /** 「怎么办」类咨询：问的是规则与流程，不是「我的数据」 */
    private static final List<String> HOW_TO = List.of(
            "怎么", "如何", "怎样", "为什么", "规则", "流程", "能不能", "可以吗", "需要什么", "要多久", "是什么");

    /** 开放疑问：问的是「有哪些」，不该用个人台账去回答 */
    private static final List<String> OPEN_QUESTION = List.of(
            "什么", "哪些", "哪个", "哪几");

    /**
     * 所有格标记。
     *
     * <p><b>刻意不包含单独一个「我」</b>：「帮我分析一下这周的活动」「给我讲讲规则」都含「我」，
     * 但它们不是「查我的数据」。仅靠单字「我」判定是旧实现的根本问题；
     * 需要「我」的场景（我积分多少 / 我订单）会由查询动作或短句长度兜住。</p>
     */
    private static final List<String> POSSESSIVE = List.of(
            "我的", "咱", "本人", "自己", "我有");

    /** 查询动作 */
    private static final List<String> QUERY_VERB = List.of(
            "查", "看", "多少", "剩", "余额", "明细", "流水", "记录", "有几", "列表");

    private static final List<String> POINTS = List.of(
            "积分", "余额", "流水", "明细", "点数");

    private static final List<String> ORDERS = List.of(
            "订单", "兑换", "提货码", "核销", "礼品");

    private static final List<String> ACTIVITIES = List.of(
            "活动", "打卡", "报名", "候补", "排队");

    private static final List<String> CERTIFICATES = List.of(
            "证书", "结项", "荣誉", "证明", "验真");

    /** 裸对象词的长度上限：「积分」「我的订单」这类短句直接按个人查询处理 */
    private static final int TERSE_LENGTH = 8;

    private AiIntentClassifier() {
    }

    /**
     * 是否属于「规则/流程」类咨询。这类问题必须交给知识库或大模型，
     * 一旦误判成个人查询就会拿账户数字去回答「怎么参加活动」。
     */
    public static boolean isHowTo(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        return containsAny(normalize(question), HOW_TO);
    }

    /**
     * 是否在问「我自己的」业务数据。
     *
     * <p>true 时调用方必须走本地工具，禁止把问题交给外部大模型。</p>
     */
    public static boolean isPersonalQuery(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String q = normalize(question);

        // 规则咨询、开放疑问都不是「查我的数据」
        if (isHowTo(q) || containsAny(q, OPEN_QUESTION)) {
            return false;
        }
        if (detectPersonalIntent(q) == Intent.KNOWLEDGE) {
            return false;
        }
        // 有明确指向：所有格、查询动作，或者本身就是「积分」这种极短的对象词
        return containsAny(q, POSSESSIVE) || containsAny(q, QUERY_VERB) || q.length() <= TERSE_LENGTH;
    }

    /**
     * 命中哪个本地工具。调用方通常先 {@link #isPersonalQuery(String)} 再据此分发。
     *
     * <p>顺序按「越具体越优先」：订单/提货码 → 积分 → 活动 → 证书。
     * 「我的订单扣了多少积分」应当按订单回答，而不是先抛一串积分流水。</p>
     */
    public static Intent detectPersonalIntent(String question) {
        if (!StringUtils.hasText(question)) {
            return Intent.KNOWLEDGE;
        }
        String q = normalize(question);
        if (containsAny(q, ORDERS)) {
            return Intent.ORDERS;
        }
        if (containsAny(q, POINTS)) {
            return Intent.POINTS;
        }
        if (containsAny(q, ACTIVITIES)) {
            return Intent.ACTIVITIES;
        }
        if (containsAny(q, CERTIFICATES)) {
            return Intent.CERTIFICATES;
        }
        return Intent.KNOWLEDGE;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String question) {
        // 去空白后比较：用户常写成「我的 积分」「我的订单 呢」
        return question.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
