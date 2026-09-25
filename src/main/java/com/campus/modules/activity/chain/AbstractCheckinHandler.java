package com.campus.modules.activity.chain;

import lombok.extern.slf4j.Slf4j;

/**
 * 签到核验抽象基类
 */
@Slf4j
public abstract class AbstractCheckinHandler implements CheckinHandler {

    @Override
    public void handle(CheckinContext context) {
        log.debug("Executing checkin handler: {}", getClass().getSimpleName());
        doHandle(context);
    }

    /**
     * 子类具体核验逻辑
     *
     * @param context 签到上下文
     */
    protected abstract void doHandle(CheckinContext context);
}
