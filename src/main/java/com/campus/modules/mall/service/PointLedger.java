package com.campus.modules.mall.service;

/**
 * 积分流水账本接口。全平台唯一的积分流水写入出口。
 * 业务语义：将已经发生（或正在发生）的积分变动记录为可对账的财务级流水。
 */
public interface PointLedger {

    /**
     * 记录一笔积分变动流水。
     * 调用方必须已经通过原子 SQL 完成余额变更，并在同一事务内读出变更后的真实可用余额。
     *
     * @param studentId   学生 ID
     * @param type        变动类型：1-活动打卡奖励 2-商城兑换消费 3-系统人工调账 4-订单取消返还
     * @param change      变动值（正数表示增加，负数表示扣减）
     * @param afterPoints 变更后当前可用积分余额
     * @param description 流水业务描述文案
     * @param refId       关联业务主键（活动参与记录 ID / 兑换订单 ID 等）
     * @param operatorId  操作人 ID（系统自动触发传 0L 或 null）
     */
    void record(Long studentId, int type, int change, int afterPoints,
                String description, Long refId, Long operatorId);

    /**
     * 记录一笔积分变动流水（显式传入业务幂等单据号 bizNo）。
     * 调用方必须已经通过原子 SQL 完成余额变更，并在同一事务内读出变更后的真实可用余额。
     *
     * @param studentId   学生 ID
     * @param type        变动类型：1-活动打卡奖励 2-商城兑换消费 3-系统人工调账 4-订单取消返还
     * @param change      变动值（正数表示增加，负数表示扣减）
     * @param afterPoints 变更后当前可用积分余额
     * @param description 流水业务描述文案
     * @param bizNo       业务幂等单据号（商城订单号 / 活动打卡参与单据号 / 调账审批单号），与 type 构成唯一约束
     * @param refId       关联业务主键（活动参与记录 ID / 兑换订单 ID 等）
     * @param operatorId  操作人 ID（系统自动触发传 0L 或 null）
     */
    void record(Long studentId, int type, int change, int afterPoints,
                String description, String bizNo, Long refId, Long operatorId);
}
