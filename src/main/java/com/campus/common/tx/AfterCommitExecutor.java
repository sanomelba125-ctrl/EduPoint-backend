package com.campus.common.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后副作用执行器。
 * 供各 Service 复用，确保「提交后执行、有事务则挂起、无事务则立即执行」，杜绝通知与缓存刷新静默丢失或异常中断。
 * 具备执行期异常隔离：Action 内部抛出的未捕获异常由本执行器安全捕获并记录日志，
 * 绝不向外冒泡影响 Spring 事务完成链路或其他回调。
 */
@Slf4j
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    /**
     * 把动作推迟到当前事务提交之后执行（当前无事务时立即执行）。
     * 用于通知推送、消息队列发布、缓存刷新、证书渲染等对外副作用，
     * 避免事务回滚时误发或无事务时静默丢失。
     *
     * @param action 事务提交后执行的副作用逻辑
     */
    public static void run(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeExecute(action);
                }
            });
            return;
        }
        safeExecute(action);
    }

    /**
     * 注册「仅在事务回滚时执行」的反向补偿，用于撤销事务内已发生但不可回滚的外部副作用。
     *
     * <p><b>典型场景：</b>秒杀链路先在 Redis 用 Lua 原子预扣库存（Redis 不参与本地事务，
     * 数据库回滚无法撤销这次 DECRBY），随后写库失败导致事务回滚 —— 若不回补，这笔库存将被永久占用，
     * 表现为「用户看到已被抢光却无人下单」的少卖。
     *
     * <p><b>与 {@link #run(Runnable)} 的区别：</b>{@code run} 在提交后执行正向动作（成功路径），
     * 本方法在回滚后执行补偿动作（失败路径），两者可同时注册，互不干扰。
     *
     * <p><b>无事务上下文时静默跳过</b>（不补偿）：此时不存在"回滚"语义，
     * 调用方应自行用 try/catch 处理失败路径，贸然补偿会造成重复回补。
     *
     * @param compensation 事务未提交（回滚或异常退出）时执行的补偿逻辑
     */
    public static void compensateOnRollback(Runnable compensation) {
        if (compensation == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务上下文：不存在回滚语义，静默跳过交由调用方 try/catch 兜底
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // 只有非「已提交」才补偿；注意超时/未知状态同样视为失败，倾向回补以避免少卖
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    safeExecute(compensation);
                }
            }
        });
    }

    private static void safeExecute(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            log.warn("[AfterCommitExecutor] 事务提交后副作用执行异常（已安全隔离，不影响事务完成）: {}", t.getMessage(), t);
        }
    }
}
