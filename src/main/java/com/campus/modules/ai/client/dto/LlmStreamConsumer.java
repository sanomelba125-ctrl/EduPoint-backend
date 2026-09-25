package com.campus.modules.ai.client.dto;

/**
 * 大模型流式接收器回调接口
 */
@FunctionalInterface
public interface LlmStreamConsumer {

    /**
     * 接收到一个流式文本片段
     *
     * @param deltaText 增量文本
     * @param finish    是否为终结片段
     */
    void onChunk(String deltaText, boolean finish);

    /**
     * 异常回调 (默认空实现)
     */
    default void onError(Throwable throwable) {
    }

    /**
     * 完成回调 (默认空实现)
     */
    default void onComplete() {
    }
}
