package com.campus.modules.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.StudentGiftService;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 礼品兑换订单业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentGiftServiceImpl extends ServiceImpl<StudentGiftMapper, StudentGift> implements StudentGiftService {

    /** 兑换幂等占位键前缀（后接 studentId:requestId，按学生维度隔离） */
    private static final String IDEMPOTENT_KEY_PREFIX = "gift:exchange:idem:";

    /** 幂等占位值：请求处理中 */
    private static final String IDEMPOTENT_PENDING = "PENDING";

    /** 幂等占位值前缀：已完成（后接订单ID，便于排查） */
    private static final String IDEMPOTENT_DONE_PREFIX = "DONE:";

    /** 处理中占位的存活时长（进程异常退出后靠它自动放开重试） */
    private static final Duration IDEMPOTENT_PENDING_TTL = Duration.ofSeconds(60);

    /** 完成态占位的保留时长（覆盖弱网重试窗口，长期防重仍由 request_id 唯一索引兜底） */
    private static final Duration IDEMPOTENT_DONE_TTL = Duration.ofHours(24);

    private final GiftMapper giftMapper;
    private final StudentMapper studentMapper;
    private final PointLedger pointLedger;
    private final SysNoticeService sysNoticeService;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftExchangeVO exchangeGift(GiftExchangeDTO dto, Long studentId) {
        // 提取或自动生成 requestId（优雅兼容旧版客户端/未显式传参场景）
        String requestId = StringUtils.hasText(dto.getRequestId())
                ? dto.getRequestId().trim()
                : "GEN-" + UUID.randomUUID().toString().replace("-", "");
        if (!StringUtils.hasText(dto.getRequestId())) {
            log.info("礼品兑换请求未显式携带 requestId 幂等标识，服务端自动补齐降级标识: studentId={}, generatedRequestId={}",
                    studentId, requestId);
        }

        // 1. 幂等快路径：同一 requestId 已成功落单时，直接返回原订单结果，
        //    不再扣减积分/库存，也不重投关单消息与发送通知（弱网重试、用户手动重试均走这里）
        StudentGift existingOrder = findOrderByRequestId(requestId, studentId);
        if (existingOrder != null) {
            log.info("礼品兑换命中幂等，返回既有订单: requestId={}, orderNo={}", requestId, existingOrder.getOrderNo());
            return buildExchangeVO(existingOrder);
        }

        // 2. Redis SETNX 占位：挡住「同一 requestId 的并发重复提交」（双端并发、传输层重发）。
        //    占位成功后才继续下单，失败说明另一次同标识请求正在处理。
        String idempotentKey = idempotentKey(studentId, requestId);
        if (!tryAcquireIdempotentSlot(idempotentKey)) {
            StudentGift doneOrder = findOrderByRequestId(requestId, studentId);
            if (doneOrder != null) {
                log.info("礼品兑换幂等占位已被占用但订单已落库，返回既有订单: requestId={}, orderNo={}",
                        requestId, doneOrder.getOrderNo());
                return buildExchangeVO(doneOrder);
            }
            log.warn("礼品兑换幂等占位被占用且订单尚未落库(可能仍在处理中): requestId={}", requestId);
            throw new BusinessException("兑换请求正在处理中，请勿重复提交！");
        }
        // 占位成功但业务失败时必须释放，否则客户端拿着同一个幂等标识再也无法重试
        releaseIdempotentSlotOnRollback(idempotentKey);

        // 3. 查询并校验学生账户状态
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生学籍档案不存在");
        }
        if (student.getStatus() != null && student.getStatus() == 0) {
            throw new BusinessException("您的学籍账号已被系统冻结，暂不可参与商城兑换，请联系学工管理员！");
        }

        // 2. 查询并校验礼品状态
        Gift gift = giftMapper.selectById(dto.getGiftId());
        if (gift == null || (gift.getDeleted() != null && gift.getDeleted() == 1)) {
            throw new BusinessException("您选择的礼品不存在");
        }
        if (gift.getStatus() == null || gift.getStatus() != 1) {
            throw new BusinessException("该礼品已暂时下架");
        }

        int quantity = (dto.getQuantity() != null && dto.getQuantity() > 0) ? dto.getQuantity() : 1;
        int totalPointsCost = gift.getPointsRequired() * quantity;

        // 5. 校验学生可用积分余额
        int currentBalance = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (currentBalance < totalPointsCost) {
            throw new BusinessException(String.format("可用积分不足！当前余额: %d pts，本次兑换需要: %d pts", currentBalance, totalPointsCost));
        }

        // 6. 校验库存并执行原子乐观锁扣减（防高并发超卖）
        if (gift.getStock() == null || gift.getStock() < quantity) {
            throw new BusinessException(String.format("礼品可用库存不足！当前仅剩 %d 件", gift.getStock() != null ? gift.getStock() : 0));
        }

        int updatedStock = giftMapper.reduceStockWithOptimisticLock(gift.getId(), quantity, gift.getVersion());
        if (updatedStock == 0) {
            log.warn("礼品兑换触发乐观锁冲突, giftId: {}, version: {}", gift.getId(), gift.getVersion());
            throw new BusinessException("当前抢兑人数较多导致冲突，请刷新后重试！");
        }

        // 7. 原子扣减学生可用积分（total_points 累计积分保持不变，确保光荣榜排名不掉）
        int updatedPoints = studentMapper.deductCurrentPoints(student.getId(), totalPointsCost);
        if (updatedPoints == 0) {
            throw new BusinessException("可用积分扣减失败，余额不足！");
        }
        int afterPoints = studentMapper.selectCurrentPoints(student.getId());

        // 8. 生成全局唯一订单号与提货码
        String orderNo = "ORD" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%04d", (int) (Math.random() * 10000));
        String pickupCode = "PK" + String.format("%04d", (int) (Math.random() * 10000))
                + String.format("%02d", (int) (Math.random() * 100));

        // 9. 保存兑换订单（request_id 唯一索引是幂等的最后一道防线，
        //    万一 Redis 不可用导致占位失效，也由数据库拒绝重复落单，整单事务回滚）
        String rName = StringUtils.hasText(dto.getReceiverName())
                ? dto.getReceiverName().trim() : student.getName();
        String rPhone = StringUtils.hasText(dto.getReceiverPhone())
                ? dto.getReceiverPhone().trim()
                : (StringUtils.hasText(student.getPhone()) ? student.getPhone() : "13911110006");
        String rAddress = StringUtils.hasText(dto.getReceiverAddress())
                ? dto.getReceiverAddress().trim() : "大学生活动中心一楼 102 创客服务台";

        StudentGift order = new StudentGift();
        order.setOrderNo(orderNo);
        order.setStudentId(studentId);
        order.setGiftId(gift.getId());
        order.setQuantity(quantity);
        order.setPointsCost(totalPointsCost);
        order.setRequestId(requestId);
        order.setReceiverName(rName);
        order.setReceiverPhone(rPhone);
        order.setReceiverAddress(rAddress);
        order.setPickupCode(pickupCode);
        order.setStatus(1); // 1-待发放/待提货
        order.setRefundPointsStatus(0);
        try {
            this.save(order);
        } catch (DuplicateKeyException e) {
            log.warn("礼品兑换订单落库命中唯一键冲突(重复提交或订单号碰撞): requestId={}, error={}", requestId, e.getMessage());
            throw new BusinessException("兑换请求已提交，请勿重复提交！如未生成订单请稍后重试");
        }

        // 10. 记录财务级积分对账台账 (由 PointLedger 统一推导 before/after 并落账)
        pointLedger.record(studentId, 2, -totalPointsCost, afterPoints,
                String.format("商城兑换礼品【%s】x %d 件 (提货码: %s)", gift.getName(), quantity, pickupCode),
                order.getId(), null);

        // 11. 把幂等占位落定为「已完成」，并投递延时关单消息、推送兑换通知。
        //     这些对外副作用统一推迟到事务提交之后：既避免回滚时误发，也避免未提交数据被外部读到。
        Long orderId = order.getId();
        String giftName = gift.getName();
        String studentName = student.getName();
        runAfterCommit(() -> {
            markIdempotentSlotDone(idempotentKey, orderId);

            // 投递延时死信关单消息 (超时未提货由死信队列自动关单并原路返还积分与库存)
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.GIFT_ORDER_EXCHANGE,
                        RabbitMqConfig.GIFT_ORDER_DELAY_ROUTING_KEY,
                        Map.of("orderId", orderId, "pickupCode", pickupCode, "studentId", studentId));
                log.info("礼品订单延时关单消息投递成功: orderId={}, orderNo={}", orderId, orderNo);
            } catch (Exception e) {
                log.warn("礼品订单延时关单消息投递失败: orderId={}, error={}", orderId, e.getMessage());
            }

            // 保存系统通知并触发 WebSocket 通知推送给在线管理端与学生端
            try {
                Map<String, Object> adminPayload = new HashMap<>();
                adminPayload.put("orderNo", orderNo);
                adminPayload.put("pickupCode", pickupCode);
                adminPayload.put("time", LocalDateTime.now().toString());

                String adminTitle = "新礼品兑换订单";
                String adminContent = String.format("学生 %s 兑换了【%s】x %d 件，订单号: %s", studentName, giftName, quantity, orderNo);
                sysNoticeService.createAndSendNotice(1, 0L, "gift_exchange", adminTitle, adminContent, orderId, adminPayload);

                // 同时记录学生端通知
                String stuTitle = "🎁 礼品兑换成功";
                String stuContent = String.format("您已成功兑换【%s】x %d 件，消耗 %d 积分，提货码：%s", giftName, quantity, totalPointsCost, pickupCode);
                sysNoticeService.createAndSendNotice(2, studentId, "gift_exchange", stuTitle, stuContent, orderId);
            } catch (Exception e) {
                log.warn("发送与记录礼品兑换通知失败: {}", e.getMessage());
            }
        });

        return GiftExchangeVO.builder()
                .orderNo(orderNo)
                .pickupCode(pickupCode)
                .pointsCost(totalPointsCost)
                .remainPoints(afterPoints)
                .giftName(giftName)
                .quantity(quantity)
                .build();
    }

    // ==================== 兑换幂等支撑 ====================

    /**
     * 按 requestId 反查已落库的兑换订单（命中即代表该次兑换意图已经成功处理过）。
     * 若该标识已被其他学生占用，说明标识被伪造或复用，直接拒绝以免串号读到他人订单。
     */
    private StudentGift findOrderByRequestId(String requestId, Long studentId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        StudentGift order = this.getOne(new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getRequestId, requestId), false);
        if (order == null) {
            return null;
        }
        if (!studentId.equals(order.getStudentId())) {
            log.warn("兑换幂等标识被非本人占用: requestId={}, ownerStudentId={}, currentStudentId={}",
                    requestId, order.getStudentId(), studentId);
            throw new BusinessException("该兑换请求标识已被占用，请重新发起兑换");
        }
        return order;
    }

    /** 幂等占位键：按学生维度隔离，避免不同学生的同名标识互相干扰 */
    private String idempotentKey(Long studentId, String requestId) {
        return IDEMPOTENT_KEY_PREFIX + studentId + ":" + requestId;
    }

    /**
     * 抢占幂等占位（Redis SETNX）。返回 true 表示本次请求拿到执行权。
     * Redis 不可用时降级放行，由 student_gift.request_id 唯一索引兜底防重复落单。
     */
    private boolean tryAcquireIdempotentSlot(String key) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(key, IDEMPOTENT_PENDING, IDEMPOTENT_PENDING_TTL));
        } catch (Exception e) {
            log.warn("兑换幂等占位写入 Redis 失败，降级由数据库唯一索引兜底: key={}, error={}", key, e.getMessage());
            return true;
        }
    }

    /** 占位落定为已完成（只在事务提交后可被其他请求看到） */
    private void markIdempotentSlotDone(String key, Long orderId) {
        try {
            redis.opsForValue().set(key, IDEMPOTENT_DONE_PREFIX + orderId, IDEMPOTENT_DONE_TTL);
        } catch (Exception e) {
            log.warn("兑换幂等占位标记完成失败(不影响订单): key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 事务回滚时释放幂等占位，保证「占位成功但业务失败」的请求可以重试。
     * 未处于事务上下文时不做处理，占位键会由 IDEMPOTENT_PENDING_TTL 自然过期。
     */
    private void releaseIdempotentSlotOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        redis.delete(key);
                    } catch (Exception e) {
                        log.warn("释放兑换幂等占位失败(将由 TTL 自然过期): key={}, error={}", key, e.getMessage());
                    }
                }
            }
        });
    }

    /** 按已落库订单组装兑换响应（幂等重放路径使用，积分余额取实时值） */
    private GiftExchangeVO buildExchangeVO(StudentGift order) {
        Gift gift = giftMapper.selectById(order.getGiftId());
        Integer remainPoints = studentMapper.selectCurrentPoints(order.getStudentId());
        return GiftExchangeVO.builder()
                .orderNo(order.getOrderNo())
                .pickupCode(order.getPickupCode())
                .pointsCost(order.getPointsCost())
                .remainPoints(remainPoints != null ? remainPoints : 0)
                .giftName(gift != null ? gift.getName() : null)
                .quantity(order.getQuantity())
                .build();
    }

    @Override
    public Page<StudentGiftVO> getMyOrders(Integer page, Integer size, Integer status, Long studentId) {
        Page<StudentGift> queryPage = new Page<>(page != null ? page : 1, size != null ? size : 10);

        LambdaQueryWrapper<StudentGift> wrapper = new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getStudentId, studentId)
                .eq(status != null, StudentGift::getStatus, status)
                .orderByDesc(StudentGift::getCreateTime);

        Page<StudentGift> orderPage = this.page(queryPage, wrapper);
        return populateOrderVOs(orderPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void studentCancelOrder(Long orderId, Long studentId) {
        StudentGift order = this.getById(orderId);
        if (order == null || !order.getStudentId().equals(studentId)) {
            throw new BusinessException("未找到对应的兑换订单");
        }

        if (order.getStatus() != 1) {
            throw new BusinessException("仅处于【待发放/待提货】状态的订单支持自主取消！");
        }

        boolean success = doCancelOrder(order, "学生在移动端自主申请取消提货", true, null);
        if (!success) {
            log.warn("学生取消订单未生效，订单状态已被并发操作流转: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            throw new BusinessException("仅处于【待发放/待提货】状态的订单支持自主取消！");
        }
    }

    @Override
    public Page<StudentGiftVO> getAdminOrderPage(GiftOrderQueryDTO queryDTO) {
        Page<StudentGift> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());

        LambdaQueryWrapper<StudentGift> wrapper = new LambdaQueryWrapper<StudentGift>()
                .like(StringUtils.hasText(queryDTO.getOrderNo()), StudentGift::getOrderNo, queryDTO.getOrderNo())
                .like(StringUtils.hasText(queryDTO.getPickupCode()), StudentGift::getPickupCode, queryDTO.getPickupCode())
                .like(StringUtils.hasText(queryDTO.getStudentName()), StudentGift::getReceiverName, queryDTO.getStudentName())
                .eq(queryDTO.getStatus() != null, StudentGift::getStatus, queryDTO.getStatus())
                .orderByDesc(StudentGift::getCreateTime);

        Page<StudentGift> orderPage = this.page(page, wrapper);
        return populateOrderVOs(orderPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDelivery(DeliveryConfirmDTO dto, Long adminId) {
        StudentGift order = this.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("兑换订单不存在");
        }

        if (order.getStatus() != 1) {
            throw new BusinessException("该订单状态不是待核销状态，不可重复操作");
        }

        // 严格比对提货码（忽略前后空格与横杠）
        String cleanInput = dto.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        String cleanTarget = order.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        if (!cleanInput.equals(cleanTarget)) {
            throw new BusinessException("提货码核销校验不匹配，核验失败！");
        }

        // 状态守卫的条件更新（status 1 -> 2）：并发下只有一方能把订单核销掉
        int changed = this.baseMapper.confirmDeliveryWithStatusGuard(order.getId(), 1, LocalDateTime.now(),
                adminId, StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : null);
        if (changed == 0) {
            log.warn("核销未生效，订单状态已被并发操作流转: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            throw new BusinessException("该订单状态不是待核销状态，不可重复操作");
        }

        // 核销通知放到事务提交之后执行，避免事务回滚时误推
        String orderNo = order.getOrderNo();
        Long studentId = order.getStudentId();
        Long orderId = order.getId();
        runAfterCommit(() -> {
            try {
                String title = "🎁 礼品已成功核销领取";
                String content = String.format("您的礼品订单【%s】已完成核销领奖！", orderNo);
                sysNoticeService.createAndSendNotice(2, studentId, "gift_send", title, content, orderId);
            } catch (Exception e) {
                log.warn("向学生发送核销通知异常: {}", e.getMessage());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminCancelOrder(OrderCancelDTO dto, Long adminId) {
        StudentGift order = this.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("兑换订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("仅【待发放】订单允许取消退单");
        }

        boolean refund = dto.getRefundPoints() == null || dto.getRefundPoints();
        if (!doCancelOrder(order, dto.getCancelReason(), refund, adminId)) {
            throw new BusinessException("仅【待发放】订单允许取消退单");
        }

        runAfterCommit(() -> {
            try {
                String stuTitle = "📢 礼品兑换订单已被管理员取消";
                String reason = StringUtils.hasText(dto.getCancelReason()) ? "原因：" + dto.getCancelReason() : "如有疑问请联系学工管理员。";
                String refundTxt = refund ? String.format("，消耗的 %d 积分已全额退还您的账户", order.getPointsCost()) : "";
                String stuContent = String.format("您的礼品订单【%s】已被管理员取消%s。%s", order.getOrderNo(), refundTxt, reason);
                sysNoticeService.createAndSendNotice(2, order.getStudentId(), "gift_cancel", stuTitle, stuContent, order.getId());
            } catch (Exception e) {
                log.warn("向学生发送管理员取消订单通知异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 取消订单的共用实现：状态守卫条件更新 + 成功后的库存/积分副作用。
     * 必须在已开启事务的 public 方法内调用（学生自主取消、管理员驳回取消、延时关单共用同一路径）。
     *
     * @return true 表示本次调用真正完成了状态流转；false 表示订单已被并发操作流转，未执行任何副作用
     */
    private boolean doCancelOrder(StudentGift order, String cancelReason, boolean refund, Long operatorId) {
        String reason = cancelReason != null ? cancelReason : "管理员审核驳回取消";
        int changed = this.baseMapper.cancelOrderWithStatusGuard(order.getId(), 1, refund ? 1 : 0,
                LocalDateTime.now(), reason, operatorId);
        if (changed == 0) {
            log.warn("订单状态已被并发操作流转，本次取消未生效: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            return false;
        }

        // 1. 原路恢复礼品可用库存并检查受影响行数（防静默失败）
        int stockRows = giftMapper.increaseStock(order.getGiftId(), order.getQuantity());
        if (stockRows <= 0) {
            log.warn("[取消订单/库存回补告警] 礼品库存回补未生效(受影响行数为0): orderId={}, orderNo={}, giftId={}, quantity={}",
                    order.getId(), order.getOrderNo(), order.getGiftId(), order.getQuantity());
        }

        // 2. 返还积分并记录流水台账
        if (refund) {
            // 退分前先判学生是否存在（拦截已注销/不存在的学生，避免 null 拆箱引发 NPE 导致 RabbitMQ 消息无限重投死循环）
            Student student = studentMapper.selectById(order.getStudentId());
            if (student == null || (student.getDeleted() != null && student.getDeleted() == 1)) {
                log.warn("[取消订单/退分告警] 学生不存在或已被注销，跳过退分及台账: studentId={}, orderId={}, orderNo={}, pointsCost={}",
                        order.getStudentId(), order.getId(), order.getOrderNo(), order.getPointsCost());
            } else {
                studentMapper.addCurrentPoints(order.getStudentId(), order.getPointsCost());
                Integer curPoints = studentMapper.selectCurrentPoints(order.getStudentId());
                int afterPoints = curPoints != null ? curPoints : 0;

                String remark;
                if (operatorId != null && operatorId == 0L) {
                    remark = String.format("订单【%s】超时未提货，系统自动取消并退还积分", order.getOrderNo());
                } else if (operatorId == null) {
                    remark = String.format("兑换订单【%s】取消，原路返还积分", order.getOrderNo());
                } else {
                    remark = String.format("管理员取消订单【%s】，全额退还积分", order.getOrderNo());
                }

                pointLedger.record(order.getStudentId(), 4, order.getPointsCost(), afterPoints,
                        remark,
                        order.getId(), operatorId);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireOrder(Long orderId) {
        StudentGift order = this.getById(orderId);
        if (order == null || (order.getDeleted() != null && order.getDeleted() == 1)) {
            log.info("[延时关单] 订单不存在或已删除，跳过: orderId={}", orderId);
            return;
        }
        // 严格幂等防线：只有待提货状态(status == 1)才执行超时自动关单
        if (order.getStatus() == null || order.getStatus() != 1) {
            log.info("[延时关单] 订单已非待提货状态(status={})，幂等退出: orderId={}", order.getStatus(), orderId);
            return;
        }

        // 复用取消路径（带状态守卫条件更新）：与学生自主取消/管理员取消并发时只会有一方生效。
        // 受影响行数为 0 时按幂等静默退出、不抛异常，避免 MQ 无限重投。
        boolean cancelled = doCancelOrder(order, "超时未提货，系统自动关单", true, 0L);
        if (cancelled) {
            log.info("[延时关单] 订单超时自动关单并退还积分成功: orderId={}, orderNo={}", orderId, order.getOrderNo());
            runAfterCommit(() -> {
                try {
                    String stuTitle = "⏰ 礼品提货已超时取消";
                    String stuContent = String.format("您的礼品订单【%s】因逾期 24 小时未到指定地点核销，系统已自动取消订单并将 %d 积分全额原路退还您的账户。",
                            order.getOrderNo(), order.getPointsCost());
                    sysNoticeService.createAndSendNotice(2, order.getStudentId(), "gift_expire", stuTitle, stuContent, order.getId());
                } catch (Exception e) {
                    log.warn("发送超时关单通知失败: {}", e.getMessage());
                }
            });
        } else {
            log.info("[延时关单] 订单已被其他并发操作流转，跳过重复退单: orderId={}", orderId);
        }
    }

    /**
     * 把动作推迟到当前事务提交之后执行（当前无事务时立即执行）。
     * 用于通知推送等「只能发生一次的对外副作用」，避免事务回滚时误发。
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private Page<StudentGiftVO> populateOrderVOs(Page<StudentGift> orderPage) {
        List<StudentGift> records = orderPage.getRecords();
        if (records.isEmpty()) {
            Page<StudentGiftVO> emptyPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
            return emptyPage;
        }

        // 批量查询关联的 Gift 与 Student 信息，避免 N+1 查询
        Set<Long> giftIds = records.stream().map(StudentGift::getGiftId).collect(Collectors.toSet());
        Set<Long> studentIds = records.stream().map(StudentGift::getStudentId).collect(Collectors.toSet());

        Map<Long, Gift> giftMap = giftMapper.selectBatchIds(giftIds).stream()
                .collect(Collectors.toMap(Gift::getId, g -> g));
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        List<StudentGiftVO> voList = records.stream().map(o -> {
            StudentGiftVO vo = new StudentGiftVO();
            BeanUtils.copyProperties(o, vo);

            Gift g = giftMap.get(o.getGiftId());
            if (g != null) {
                vo.setGiftName(g.getName());
                vo.setGiftImage(g.getImage());
            }

            Student s = studentMap.get(o.getStudentId());
            if (s != null) {
                vo.setStudentNo(s.getStudentNo());
                vo.setStudentName(s.getName());
                vo.setClassName(s.getClassName());
            }

            return vo;
        }).collect(Collectors.toList());

        Page<StudentGiftVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public GiftOrderMetricsVO getOrderMetrics() {
        GiftOrderMetricsVO metrics = this.baseMapper.selectOrderMetrics();
        if (metrics == null) {
            metrics = new GiftOrderMetricsVO();
            metrics.setTotalCount(0L);
            metrics.setPendingCount(0L);
            metrics.setCompletedCount(0L);
            metrics.setCancelledCount(0L);
            metrics.setTotalPoints(0L);
        }
        return metrics;
    }
}
