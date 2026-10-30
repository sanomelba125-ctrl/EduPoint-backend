package com.campus.modules.ai.service;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * AI 对话的意图分流规则（2026-10-29 修复 P0-4，2026-10-30 收紧边界）。
 *
 * <h3>为什么需要它</h3>
 * 平台有一条硬性隐私约束：<b>个人私有数据（积分、订单、提货码、证书等）绝不出域</b>，
 * 只能由本地 {@code ToolRegistry} 查询，不能交给外部大模型。于是对话入口必须先判断
 * 「这句话是不是在问我的个人数据」，再决定走本地工具还是走大模型。
 *
 * <p>这条判断历史上翻过三次车，每一次都记在这里，因为它就是「规则系统没有银弹」的现场教材：
 * <ol>
 *   <li><b>第一版</b> {@code question.contains("我")}：把「我<b>怎么</b>参加活动拿积分」判成个人查询
 *       （本地按「积分」返回余额，答非所问），却漏掉「查询积分流水」这种不带「我」的个人查询
 *       （交给大模型后个人工具 {@code isPublic=false} 不暴露，只能回一句「请查看上方查询结果」，而上方空白）。</li>
 *   <li><b>第二版</b> 干脆删掉单字「我」只认「我的」：于是「我兑换的礼品提货码」「我报名成功的活动呢」
 *       这类口语化提问全部漏判，被知识库按单关键词（提货码/报名）截胡，回一段通用规则 FAQ。</li>
 *   <li><b>第三版</b>（当前）用「我 + 动作/对象」正则覆盖口语所有格，并把「短句即个人查询」的长度
 *       从 8 收紧到 4——8 字会把「活动报名时间」「礼品核销有效期」「现场扫码打卡」这类
 *       <b>公开信息咨询</b>全部吞成个人台账。</li>
 * </ol>
 *
 * <h3>现在的判定顺序（顺序本身就是设计）</h3>
 * <pre>
 * 0. 没有任何个人业务对象（积分/订单/活动/证书…）        → 不是个人查询
 * 1. 规则流程咨询（怎么/如何/规则/流程/能不能…）        → 不是（交给知识库）
 * 2. 开放疑问（什么/哪些/哪个…）                        → 不是（问「有哪些」，不是「我有多少」）
 * 3. 求建议（分析/推荐/介绍/攻略/想参加…）              → 不是（要的是主意，不是台账）
 * 4. 明确指向自己（我的/咱/本人/我有，或「我+动作」）    → 是
 * 5. 查询动作（多少/余额/明细/流水/记录/有几）          → 是
 * 6. 「查/看 + 个人业务对象」（查积分 / 查订单）         → 是
 * 7. 极短裸对象词（≤4 字且是个人对象：「积分」「提货码」）→ 是
 * </pre>
 *
 * <p>顺序不是随便排的：第 1~3 步必须压在「我」前面，否则「我<b>怎么</b>参加活动」「帮我<b>分析</b>一下我的积分」
 * 会被单字「我」误伤；第 4 步必须压在查询动作前面，否则「我兑换的礼品提货码」在口语里找不到「查/多少」就会漏判。</p>
 *
 * <p>已知刻意保留的边界：「活动还剩几分钟结束」这类公开信息里的「剩」不再当作查询动作
 * （否则会被答成个人活动台账），代价是「我还剩几个积分」必须靠第 4 步的「我+积分」兜住。</p>
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
     * 求建议/求攻略：要的是主意，不是自己的台账。
     *
     * <p>「想参加」「想报名」这类「意愿表达」也放这里——「我想参加活动」是在找活动，
     * 不是要人把「我的活动」念一遍。但「我想<b>查</b>我的积分」不会被误伤，
     * 因为「想查」不在列表里。</p>
     */
    private static final List<String> ADVISORY = List.of(
            "分析", "推荐", "介绍", "攻略", "值得", "建议",
            "想参加", "想报名", "想兑换", "想了解", "想咨询", "想买");

    /**
     * 显式所有格标记。
     *
     * <p>单字「我」不在这里，改由 {@link #SELF_ACTION} 用「我 + 动作/对象」正则覆盖——
     * 直接认单字「我」正是第一版的事故来源。</p>
     */
    private static final List<String> POSSESSIVE = List.of(
            "我的", "咱", "本人", "自己", "我有");

    /**
     * 「我 + 动作/对象」：覆盖口语里省掉「的」的所有格。
     *
     * <p>命中示例：「我兑换的礼品提货码」「我刚兑换的礼品提货码呢」「我报名成功的活动呢」「我打卡了吗」
     * 「我还剩几个积分」。中间允许 0~4 个非标点字符，正好容下「刚/昨天/上次/还剩几个」这类状语；
     * 遇到逗号问号就断开，避免跨句误伤。</p>
     */
    private static final Pattern SELF_ACTION = Pattern.compile(
            "我[^，。？！,?!、；;：:]{0,4}(积分|余额|流水|明细|订单|兑换|提货码|核销|礼品"
                    + "|活动|参加|预约|打卡|报名|候补|排队|证书|结项|荣誉|证明)");

    /**
     * 查询动作（只放与「对象」无关、单看就说明在要数的词）。
     *
     * <p>刻意<b>不含</b>以下三类，它们单独出现都代表不了「查我的数据」：</p>
     * <ul>
     *   <li>「看」「列表」——太泛：「看看这周的活动安排」「活动列表在哪」都不是查个人数据；
     *       真正查个人的说法（看看我的订单 / 我的提货码在哪看）都能被所有格兜住。</li>
     *   <li>「剩」——「活动还剩几分钟结束」会被误判成个人活动查询；「还剩多少积分」本来就有「多少」。</li>
     *   <li><b>单字「查」</b>（2026-10-30 移除）——「查」可以接任何东西：「查询志愿活动」「查活动」
     *       「查询校园活动」都是<b>逛公开活动大厅</b>，一旦只凭「查」就判个人查询，会被答成
     *       「你当前暂无已报名或候补中的活动」。要表达「查我的」，请用下面的
     *       {@link #PERSONAL_OBJECT_QUERY}（查/看 + 个人业务对象）或所有格。</li>
     * </ul>
     */
    private static final List<String> QUERY_VERB = List.of(
            "多少", "余额", "明细", "流水", "记录", "有几");

    /**
     * 「查/看 + 个人业务对象」：动词必须紧贴一个<b>天然属于个人</b>的对象才算查个人数据。
     *
     * <p>命中示例：「查积分」「查订单」「查看提货码」「查一下兑换记录」（最后一句另有「记录」兜底）。
     * <b>刻意不收录</b>「活动」「礼品」「打卡」「报名」——这些对象本身是公开目录，
     * 「查活动」「查礼品」更可能是在逛大厅，交给知识库/大模型更合理。</p>
     */
    private static final Pattern PERSONAL_OBJECT_QUERY = Pattern.compile(
            "(查|查询|查看|看)[^，。？！,?!、；;：:]{0,3}(积分|余额|流水|明细|订单|提货码|核销|证书|荣誉|证明)");

    private static final List<String> POINTS = List.of(
            "积分", "余额", "流水", "明细", "点数");

    private static final List<String> ORDERS = List.of(
            "订单", "兑换", "提货码", "核销", "礼品");

    private static final List<String> ACTIVITIES = List.of(
            "活动", "打卡", "报名", "候补", "排队");

    private static final List<String> CERTIFICATES = List.of(
            "证书", "结项", "荣誉", "证明", "验真");

    /**
     * 裸对象词的长度上限。
     *
     * <p>只用来兜「积分」「订单」「提货码」这种纯名词短语。取 4 而不是 8：
     * 8 会把「活动报名时间」（6 字）、「礼品核销有效期」（7 字）、「现场扫码打卡」（6 字）
     * 这类<b>公开信息咨询</b>误判成个人查询。</p>
     */
    private static final int TERSE_LENGTH = 4;

    /**
     * 允许「只说一个词」就判为个人查询的对象。
     *
     * <p>只有<b>天然属于个人</b>的对象才在列：积分、余额、订单、提货码、证书……
     * 光说「积分」几乎只可能是查自己的余额；而光说「活动」「礼品」「打卡」更可能是在逛大厅，
     * 所以它们不在列表里——这类词必须带上所有格（我的活动）或口语所有格（我报名的活动）才算。
     * 这一条修的是「查活动」「活动」被答成「你当前暂无已报名或候补中的活动」的误杀。</p>
     */
    private static final List<String> TERSE_OBJECTS = List.of(
            "积分", "余额", "流水", "明细", "点数",
            "订单", "提货码", "核销",
            "证书", "结项", "荣誉", "证明", "验真");

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

        // 0. 通篇没有任何个人业务对象（积分/订单/活动/证书…）→ 一定不是个人数据查询。
        //    放在最前面是因为它最便宜，且能挡住「我的意思是」「帮我分享到朋友圈」这类误伤。
        if (detectPersonalIntent(q) == Intent.KNOWLEDGE) {
            return false;
        }

        // 1~3. 规则咨询 / 开放疑问 / 求建议：都问不到「我的」数据上
        if (isHowTo(q) || containsAny(q, OPEN_QUESTION) || containsAny(q, ADVISORY)) {
            return false;
        }

        // 4. 明确指向自己（所有格，或口语化的「我+动作」）
        if (containsAny(q, POSSESSIVE) || SELF_ACTION.matcher(q).find()) {
            return true;
        }

        // 5. 查询动作（多少/余额/明细/流水/记录/有几，与对象无关）
        if (containsAny(q, QUERY_VERB)) {
            return true;
        }

        // 6. 「查/看 + 个人业务对象」：动词必须贴着一个天然属于个人的对象
        if (PERSONAL_OBJECT_QUERY.matcher(q).find()) {
            return true;
        }

        // 7. 极短裸对象词，且必须是天然属于个人的对象（「积分」算，「活动/礼品」不算）
        return q.length() <= TERSE_LENGTH && containsAny(q, TERSE_OBJECTS);
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
