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
