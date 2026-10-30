package com.campus.modules.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 对话意图分流规则测试（2026-10-29，P0-4）。
 *
 * <p>锁住两条线上事故：
 * <ol>
 *   <li>「我<b>怎么</b>参加活动拿积分」被判成个人查询，本地按「积分」返回余额，答非所问；</li>
 *   <li>「查询积分流水」不含「我」，被判成非个人查询，交给大模型后又查不到个人数据，
 *       只能回一句「请查看上方查询结果」，而上方什么都没有。</li>
 * </ol>
 *
 * <p>规则是纯字符串判定，最适合表驱动测试：新增关键词时在这里补一行即可。
 */
class AiIntentClassifierTest {

    @Test
    @DisplayName("P0-4：规则/流程类咨询不得判成个人查询（否则会拿账户数字回答「怎么参加活动」）")
    void howToQuestionsMustNotBePersonal() {
        String[] howToQuestions = {
                "我怎么参加活动拿积分",
                "如何获得积分",
                "积分规则是什么",
                "活动报名流程",
                "礼品兑换需要多久",
                "证书能不能补办",
                "打卡失败了怎么办"
        };
        for (String question : howToQuestions) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」是规则咨询，不能走个人台账查询");
        }
    }

    @Test
    @DisplayName("P0-4：开放疑问（有什么/哪些）不得判成个人查询")
    void openQuestionsMustNotBePersonal() {
        String[] openQuestions = {
                "有什么活动",
                "哪些礼品可以兑换",
                "最近有什么打卡活动"
        };
        for (String question : openQuestions) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」问的是「有哪些」，不该用个人台账回答");
        }
    }

    @Test
    @DisplayName("P0-4：不带「我」的个人查询也要识别出来（旧实现漏判这一整类）")
    void personalQueryWithoutPronounMustBeDetected() {
        String[] personalQuestions = {
                "查询积分流水",
                "还剩多少积分",
                "积分",
                "看看我的订单",
                "我的提货码在哪看",
                "查一下兑换记录",
                "我的证书",
                "我有几个证书",
                "我的 积分"          // 带空格也要能识别（normalize 会去空白）
        };
        for (String question : personalQuestions) {
            assertTrue(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」明显是在问个人数据，必须走本地工具，不能发给外部大模型");
        }
    }

    @Test
    @DisplayName("P0-4：闲聊与无关问题不得判成个人查询")
    void smallTalkMustNotBePersonal() {
        for (String question : new String[]{"你好", "谢谢", "今天天气怎么样", "你是谁"}) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question), "「" + question + "」与个人台账无关");
        }
    }

    @Test
    @DisplayName("P0-4：意图分发按「越具体越优先」，订单先于积分")
    void intentDetectionPrefersTheMoreSpecificObject() {
        assertEquals(AiIntentClassifier.Intent.ORDERS, AiIntentClassifier.detectPersonalIntent("我的订单"));
        assertEquals(AiIntentClassifier.Intent.ORDERS, AiIntentClassifier.detectPersonalIntent("我的订单扣了多少积分"));
        assertEquals(AiIntentClassifier.Intent.ORDERS, AiIntentClassifier.detectPersonalIntent("提货码在哪"));
        assertEquals(AiIntentClassifier.Intent.POINTS, AiIntentClassifier.detectPersonalIntent("我的积分"));
        assertEquals(AiIntentClassifier.Intent.POINTS, AiIntentClassifier.detectPersonalIntent("积分流水"));
        assertEquals(AiIntentClassifier.Intent.ACTIVITIES, AiIntentClassifier.detectPersonalIntent("我的活动"));
        assertEquals(AiIntentClassifier.Intent.ACTIVITIES, AiIntentClassifier.detectPersonalIntent("我候补排第几"));
        assertEquals(AiIntentClassifier.Intent.CERTIFICATES, AiIntentClassifier.detectPersonalIntent("我的证书"));
        assertEquals(AiIntentClassifier.Intent.KNOWLEDGE, AiIntentClassifier.detectPersonalIntent("你好"));
    }

    @Test
    @DisplayName("收紧边界①：公开信息咨询不能被「短句即个人查询」吞掉（8 字阈值太宽）")
    void shortPublicInfoQuestionsMustNotBePersonal() {
        // 这几句都是 6~9 字、不含「怎么/什么」的公开信息咨询。
        // 旧阈值 TERSE_LENGTH=8 会把它们一律判成个人查询，然后拿「我的活动/我的订单」去回答。
        String[] publicInfoQuestions = {
                "活动报名时间",
                "礼品核销有效期",
                "现场扫码打卡",
                "活动地点在哪",
                "看看这周的活动安排",
                "活动还剩几分钟结束",
                "积分排行榜怎么看"
        };
        for (String question : publicInfoQuestions) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」问的是公开信息，不能用个人台账回答");
        }
    }

    @Test
    @DisplayName("收紧边界②：口语化「我+动作」不能漏判（只认「我的」会漏掉一整类说法）")
    void colloquialSelfReferenceMustBePersonal() {
        // 这些句子没有「我的」，长度也超过 4 字，但意思非常明确：查我自己的东西。
        // 漏判的后果是被知识库按「提货码」「报名」等单关键词截胡，回一段通用规则 FAQ。
        String[] colloquialPersonal = {
                "我兑换的礼品提货码",
                "我刚兑换的礼品提货码呢",
                "我报名成功的活动呢",
                "我打卡了吗",
                "我还剩几个积分",
                "我参加的活动的积分到账了吗",
                "我昨天兑换的订单到哪了"
        };
        for (String question : colloquialPersonal) {
            assertTrue(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」是明显的个人数据查询，漏判会让用户拿不到自己的提货码/活动");
        }
    }

    @Test
    @DisplayName("收紧边界③：求建议/表意愿的句子不得走个人台账")
    void advisoryQuestionsMustNotBePersonal() {
        String[] advisory = {
                "我想报名这周的活动",
                "我想参加活动",
                "我想兑换礼品",
                "推荐几个活动",
                "帮我分析一下我的积分消费"
        };
        for (String question : advisory) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」要的是建议，不是自己的台账");
        }
    }

    @Test
    @DisplayName("收紧边界④：单字「查」不得把公开对象截胡（查活动 ≠ 查我的活动）")
    void bareChaMustNotHijackPublicObjects() {
        // 这些句子都在问「平台上有哪些活动/礼品」，不是「我报名了哪些」。
        // 旧规则只要有「查」就判个人查询，会被答成「你当前暂无已报名或候补中的活动」。
        String[] publicQueries = {
                "查询志愿活动",
                "查活动",
                "查询校园活动",
                "查礼品",
                "查询近期讲座"
        };
        for (String question : publicQueries) {
            assertFalse(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」是在逛公开大厅，不能用个人台账回答");
        }
    }

    @Test
    @DisplayName("收紧边界⑤：「查/看 + 个人对象」仍必须判为个人查询")
    void queryWithPersonalObjectIsStillPersonal() {
        String[] personalQueries = {
                "查积分",
                "查订单",
                "查看提货码",
                "查一下证书",
                "查我的余额"
        };
        for (String question : personalQueries) {
            assertTrue(AiIntentClassifier.isPersonalQuery(question),
                    "「" + question + "」是查自己的数据，漏判会让用户拿不到结果");
        }
    }

    @Test
    @DisplayName("收紧边界⑥：极短裸词只在「天然属于个人」的对象上生效")
    void terseWordOnlyWorksForPersonalObjects() {
        assertTrue(AiIntentClassifier.isPersonalQuery("积分"), "「积分」几乎只可能是查自己余额");
        assertTrue(AiIntentClassifier.isPersonalQuery("订单"));
        assertTrue(AiIntentClassifier.isPersonalQuery("提货码"));

        // 「活动」「礼品」「打卡」本身就是公开目录词，光说两个字更可能是在逛大厅
        assertFalse(AiIntentClassifier.isPersonalQuery("活动"), "「活动」应去查活动大厅而不是我的记录");
        assertFalse(AiIntentClassifier.isPersonalQuery("礼品"), "「礼品」应去礼品商城");
        assertFalse(AiIntentClassifier.isPersonalQuery("打卡"));
    }

    @Test
    @DisplayName("P0-4：旧实现用 contains(\"分\") 误判的词，不能再被当成积分查询")
    void bareFenMustNotBeTreatedAsPoints() {
        // 「分析」「分钟」「分享」都含「分」，但它们不是积分查询
        assertFalse(AiIntentClassifier.isPersonalQuery("帮我分析一下这周的活动"),
                "「分析」不是「积分」");
        assertNotEquals(AiIntentClassifier.Intent.POINTS,
                AiIntentClassifier.detectPersonalIntent("活动还剩几分钟结束"),
                "「分钟」不能被当成积分意图");
        assertNotEquals(AiIntentClassifier.Intent.POINTS,
                AiIntentClassifier.detectPersonalIntent("帮我分享到朋友圈"),
                "「分享」不能被当成积分意图");
    }
}
