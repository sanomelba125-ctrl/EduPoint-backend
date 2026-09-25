package com.campus.modules.activity.chain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 签到打卡责任链执行流水线 (Chain Pipeline)
 */
@Slf4j
@Component
public class CheckinChainPipeline {

    private final List<CheckinHandler> handlers;

    public CheckinChainPipeline(List<CheckinHandler> handlers) {
        List<CheckinHandler> sorted = new ArrayList<>(handlers != null ? handlers : List.of());
        sorted.sort(Comparator.comparingInt(CheckinHandler::getOrder));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getOrder() == sorted.get(i - 1).getOrder()) {
                throw new IllegalStateException(String.format(
                        "签到责任链 order 冲突：%s 与 %s 都返回 %d，执行顺序将不确定",
                        sorted.get(i - 1).getClass().getSimpleName(),
                        sorted.get(i).getClass().getSimpleName(),
                        sorted.get(i).getOrder()));
            }
        }
        this.handlers = sorted;
        log.info("CheckinChainPipeline initialized with {} handlers: {}",
                this.handlers.size(),
                this.handlers.stream().map(h -> h.getClass().getSimpleName()).toList());
    }

    /**
     * 按预定顺序执行责任链中的所有核验节点
     *
     * @param context 签到核销上下文
     */
    public void execute(CheckinContext context) {
        for (CheckinHandler handler : handlers) {
            handler.handle(context);
        }
    }

    public List<CheckinHandler> getHandlers() {
        return handlers;
    }
}
