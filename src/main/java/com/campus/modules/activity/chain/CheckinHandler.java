package com.campus.modules.activity.chain;

/**
 * 签到打卡核验节点接口 (Chain of Responsibility Pattern)
 */
public interface CheckinHandler {

    /**
     * 执行核验逻辑，若核验失败则直接抛出 BusinessException
     *
     * @param context 签到上下文
     */
    void handle(CheckinContext context);

    /**
     * 节点在责任链中的执行顺序，数字越小越先执行。
     *
     * <p><b>这是唯一的顺序权威</b>：{@link CheckinChainPipeline} 在构造时按本方法的返回值排序。
     * 不要在本接口的实现类上添加 Spring 的 {@code @Order} 注解——那个注解控制的是 Bean 注入顺序，
     * 而注入顺序会被 Pipeline 的内部排序覆盖，加在这里只会制造「改了注解却不生效」的维护陷阱。
     *
     * @return 排序号
     */
    int getOrder();
}
