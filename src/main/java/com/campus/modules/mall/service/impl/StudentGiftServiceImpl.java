package com.campus.modules.mall.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.common.util.QrCodeUtil;
import com.campus.config.MallPickupProperties;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.enums.GiftStatus;
import com.campus.modules.mall.enums.OrderStatus;
import com.campus.modules.student.enums.StudentStatus;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.GiftCacheService;
import com.campus.modules.mall.service.GiftIdempotentService;
import com.campus.common.tx.AfterCommitExecutor;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.PickupCodeGenerator;
import com.campus.modules.mall.service.PickupTokenService;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.StudentGiftService;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.service.SysNoticeService;
import com.alibaba.fastjson2.JSON;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.campus.common.tx.AfterCommitExecutor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 礼品兑换订单业务实现
 */

@Slf4j
@Service
public class StudentGiftServiceImpl extends ServiceImpl<StudentGiftMapper, StudentGift> implements StudentGiftService {

    /** 商城默认取件点地址（学生未填收件地址时兜底）。 */
    private static final String DEFAULT_PICKUP_ADDRESS = "大学生活动中心一楼 102 创客服务台";
    /** 学生档案也无手机号时的兜底联系号码。 */
    private static final String DEFAULT_RECEIVER_PHONE = "13911110006";

    /** 校验学生可参与兑换并返回其档案；不存在或已冻结一律拒绝。 */
    private Student requireExchangeableStudent(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "学生学籍档案不存在");
        }
        if (student.getStatus() != null && student.getStatus() == StudentStatus.DISABLED.getCode()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED.getCode(),
                    "您的学籍账号已被系统冻结，暂不可参与商城兑换，请联系学工管理员！");
        }
        return student;
    }

    /** 校验礼品存在且已上架；下架/删除一律拒绝。 */
    private Gift requireOnShelfGift(Long giftId) {
        Gift gift = giftMapper.selectById(giftId);
        if (gift == null || (gift.getDeleted() != null && gift.getDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "您选择的礼品不存在");
        }
        if (gift.getStatus() == null || gift.getStatus() != GiftStatus.ON_SHELF.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该礼品已暂时下架");
        }
        return gift;
    }

    /** 兑换收件人三元组：前端未填时回落到学生档案，再回落到商城默认取件点。 */
    private record ReceiverInfo(String name, String phone, String address) {}

    /** 统一收件人兜底口径。 */
    private ReceiverInfo resolveReceiver(GiftExchangeDTO dto, Student student) {
        String name = StringUtils.hasText(dto.getReceiverName()) ? dto.getReceiverName().trim() : student.getName();
        String phone = StringUtils.hasText(dto.getReceiverPhone()) ? dto.getReceiverPhone().trim()
                : (StringUtils.hasText(student.getPhone()) ? student.getPhone() : DEFAULT_RECEIVER_PHONE);
        String address = StringUtils.hasText(dto.getReceiverAddress()) ? dto.getReceiverAddress().trim() : DEFAULT_PICKUP_ADDRESS;
        return new ReceiverInfo(name, phone, address);
    }

    private final GiftMapper giftMapper;
    private final StudentMapper studentMapper;
    private final PointLedger pointLedger;
    private final SysNoticeService sysNoticeService;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;
    private final PickupTokenService pickupTokenService;
    private final MallPickupProperties mallPickupProperties;
    private final GiftIdempotentService giftIdempotentService;
    private final com.campus.common.metrics.CampusMetricsCollector campusMetricsCollector;

    private final GiftStockRedisService giftStockRedisService;
    private final LocalMessageService localMessageService;
    private final GiftCacheService giftCacheService;
    private final PickupCodeGenerator pickupCodeGenerator = new PickupCodeGenerator();

    @Autowired
    public StudentGiftServiceImpl(GiftMapper giftMapper,
                                  StudentMapper studentMapper,
                                  PointLedger pointLedger,
                                  SysNoticeService sysNoticeService,
                                  RabbitTemplate rabbitTemplate,
                                  StringRedisTemplate redis,
                                  PickupTokenService pickupTokenService,
                                  MallPickupProperties mallPickupProperties,
                                  GiftIdempotentService giftIdempotentService,
                                  com.campus.common.metrics.CampusMetricsCollector campusMetricsCollector,
                                  GiftStockRedisService giftStockRedisService,
                                  LocalMessageService localMessageService,
                                  GiftCacheService giftCacheService) {
        this.giftMapper = giftMapper;
        this.studentMapper = studentMapper;
        this.pointLedger = pointLedger;
        this.sysNoticeService = sysNoticeService;
        this.rabbitTemplate = rabbitTemplate;
        this.redis = redis;
        this.mallPickupProperties = mallPickupProperties;
        this.pickupTokenService = pickupTokenService;
        this.giftIdempotentService = giftIdempotentService;
        this.campusMetricsCollector = campusMetricsCollector;
        this.giftStockRedisService = giftStockRedisService;
        this.localMessageService = localMessageService;
        this.giftCacheService = giftCacheService;
    }

    @Override
    public GiftExchangeTokenVO getExchangeToken(Long giftId, Long studentId) {
        if (giftId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "礼品ID不能为空");
        }
        if (studentId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "学生未登录或身份异常");
        }

        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "学生学籍档案不存在");
        }
        if (student.getStatus() != null && student.getStatus() == StudentStatus.DISABLED.getCode()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED.getCode(), "您的学籍账号已被系统冻结，暂不可参与商城兑换，请联系学工管理员！");
        }

        Gift gift = giftMapper.selectById(giftId);
        if (gift == null || (gift.getDeleted() != null && gift.getDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "您选择的礼品不存在");
        }
        if (gift.getStatus() == null || gift.getStatus() != GiftStatus.ON_SHELF.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该礼品已暂时下架");
        }

        return giftIdempotentService.generateExchangeToken(studentId, giftId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftExchangeVO exchangeGift(GiftExchangeDTO dto, Long studentId) {
        long startTime = System.currentTimeMillis();

        // 提取或自动生成 requestId（优先使用前端显式传入的 requestId，或回传的 exchangeToken）

        String requestId = StringUtils.hasText(dto.getRequestId())
                ? dto.getRequestId().trim()
                : (StringUtils.hasText(dto.getExchangeToken())
                        ? dto.getExchangeToken().trim()
                        : "GEN-" + UUID.randomUUID().toString().replace("-", ""));
        if (!StringUtils.hasText(dto.getRequestId()) && !StringUtils.hasText(dto.getExchangeToken())) {
            log.info("礼品兑换请求未显式携带 requestId 幂等标识，服务端自动补齐降级标识: studentId={}, generatedRequestId={}",
                    studentId, requestId);
        }

        // 1. 幂等快路径：同一 (studentId, requestId) 已成功落单时，直接返回原订单结果，

        //    不再扣减积分/库存，也不重投关单消息与发送通知（弱网重试、用户手动重试均走这里）

        StudentGift existingOrder = findOrderByRequestId(requestId, studentId);
        if (existingOrder != null) {
            log.info("礼品兑换命中既有订单(弱网重试/幂等快路径): studentId={}, requestId={}, orderNo={}",
                    studentId, requestId, existingOrder.getOrderNo());
            return buildExchangeVO(existingOrder);
        }

        // 2. Redis SETNX 占位：挡住「同一 (studentId, requestId) 的并发重复提交」（双端并发、传输层重发）。

        //    占位成功后才继续下单，失败说明另一次同标识请求正在处理。

        if (!giftIdempotentService.tryAcquireSlot(studentId, requestId)) {
            StudentGift doneOrder = findOrderByRequestId(requestId, studentId);
            if (doneOrder != null) {
                log.info("礼品兑换幂等占位已被占用但订单已落库，返回既有订单: studentId={}, requestId={}, orderNo={}",
                        studentId, requestId, doneOrder.getOrderNo());
                return buildExchangeVO(doneOrder);
            }

            log.warn("礼品兑换幂等占位被占用且订单尚未落库(可能仍在处理中): studentId={}, requestId={}", studentId, requestId);
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "兑换请求正在处理中，请勿重复提交！");
        }

        // 占位成功但后续业务失败/回滚时必须释放，否则客户端无法重试

        giftIdempotentService.releaseSlotOnRollback(studentId, requestId);

        // 3. 服务端防重 Token 校验 (Token 桶一次性机制，核验即焚；若客户端缺失 Token 则由全局幂等槽位与数据库唯一索引兜底)

        String tokenToVerify = StringUtils.hasText(dto.getExchangeToken())
                ? dto.getExchangeToken().trim()
                : (StringUtils.hasText(dto.getRequestId()) && dto.getRequestId().startsWith("EXT-")
                        ? dto.getRequestId().trim()
                        : null);
        if (StringUtils.hasText(tokenToVerify)) {
            boolean tokenValid = giftIdempotentService.verifyAndConsumeToken(studentId, dto.getGiftId(), tokenToVerify);
            if (!tokenValid) {
                log.warn("礼品兑换防重令牌核验失败(已失效或已被使用): studentId={}, giftId={}, token={}",
                        studentId, dto.getGiftId(), tokenToVerify);
                throw new BusinessException(ResultCode.EXCHANGE_TOKEN_INVALID.getCode(), "兑换令牌已失效或已被使用，请重试！");
            }
        }

        // 4. 查询并校验学生账户状态与礼品状态（统一口径）
        Student student = requireExchangeableStudent(studentId);
        Gift gift = requireOnShelfGift(dto.getGiftId());

        int quantity = (dto.getQuantity() != null && dto.getQuantity() > 0) ? dto.getQuantity() : 1;
        int totalPointsCost = gift.getPointsRequired() * quantity;

        // 5. 校验学生可用积分余额
        int currentBalance = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (currentBalance < totalPointsCost) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH.getCode(), String.format("可用积分不足！当前余额: %d 积分，本次兑换需要: %d 积分", currentBalance, totalPointsCost));
        }

        // 6. 校验库存并执行原子乐观锁扣减（P1-7: 统一乐观锁冲突重试策略，最大重试 3 次 + 指数退避）
        if (gift.getStock() == null || gift.getStock() < quantity) {
            if (gift.getStock() != null && gift.getStock() <= 0) {
                throw new BusinessException(ResultCode.GIFT_STOCK_EMPTY.getCode(), "礼品库存已被抢光！");
            }
            throw new BusinessException(ResultCode.GIFT_STOCK_EMPTY.getCode(), String.format("礼品可用库存不足！当前仅剩 %d 件", gift.getStock() != null ? gift.getStock() : 0));
        }

        int updatedStock = 0;
        Gift candidateGift = gift;
        for (int attempt = 0; attempt < 3; attempt++) {
            updatedStock = giftMapper.reduceStockWithOptimisticLock(candidateGift.getId(), quantity, candidateGift.getVersion());
            if (updatedStock > 0) {
                break;
            }
            log.warn("礼品兑换触发乐观锁冲突重试, attempt: {}, giftId: {}, version: {}", attempt + 1, candidateGift.getId(), candidateGift.getVersion());
            candidateGift = giftMapper.selectById(candidateGift.getId());
            if (candidateGift == null || candidateGift.getStock() == null || candidateGift.getStock() < quantity) {
                break;
            }
            try {
                Thread.sleep(50L * (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (updatedStock == 0) {
            log.warn("礼品兑换触发乐观锁冲突重试耗尽, giftId: {}, version: {}", gift.getId(), gift.getVersion());
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "当前抢兑人数较多导致冲突，请刷新后重试！");
        }

        // 7. 原子扣减学生可用积分（total_points 累计积分保持不变，确保光荣榜排名不掉）

        int updatedPoints = studentMapper.deductCurrentPoints(student.getId(), totalPointsCost);
        if (updatedPoints == 0) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH.getCode(), "可用积分扣减失败，余额不足！");
        }

        int afterPoints = studentMapper.selectCurrentPoints(student.getId());

        // 8. 生成全局唯一订单号与加密防碰撞提货码

        String orderNo = generateOrderNo();
        String pickupCode = generateUniquePickupCode();

        // 9. 保存兑换订单（request_id 唯一索引是幂等的最后一道防线，

        //    万一 Redis 不可用导致占位失效，也由数据库拒绝重复落单，整单事务回滚）

        ReceiverInfo receiver = resolveReceiver(dto, student);
        StudentGift order = new StudentGift();
        order.setOrderNo(orderNo);
        order.setStudentId(studentId);
        order.setGiftId(gift.getId());
        order.setQuantity(quantity);
        order.setPointsCost(totalPointsCost);
        order.setRequestId(requestId);
        order.setReceiverName(receiver.name());
        order.setReceiverPhone(receiver.phone());
        order.setReceiverAddress(receiver.address());
        order.setPickupCode(pickupCode);
        order.setStatus(OrderStatus.PENDING);
        order.setRefundPointsStatus(0);
        try {
            this.save(order);
        } catch (DuplicateKeyException e) {
            log.warn("礼品兑换订单落库命中联合唯一键冲突(重复提交或订单号碰撞): studentId={}, requestId={}, error={}",
                    studentId, requestId, e.getMessage());
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "兑换请求已提交，请勿重复提交！如未生成订单请稍后重试");
        }

        // 10. 记录财务级积分对账台账 (由 PointLedger 统一推导 before/after 并落账)
        pointLedger.record(studentId, com.campus.modules.mall.entity.PointRecordType.GIFT_EXCHANGE.getCode(), -totalPointsCost, afterPoints,
                String.format("商城兑换礼品【%s】x %d 件 (提货码: %s)", gift.getName(), quantity, pickupCode),
                order.getOrderNo(), order.getId(), null);

        // 11. 把幂等占位落定为「已完成」，并投递延时关单消息、推送兑换通知。

        //     这些对外副作用统一推迟到事务提交之后：既避免回滚时误发，也避免未提交数据被外部读到。

        Long orderId = order.getId();
        String giftName = gift.getName();
        String studentName = student.getName();
        AfterCommitExecutor.run(() -> {
            giftIdempotentService.markSlotDone(studentId, requestId, orderId);

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
        if (campusMetricsCollector != null) {
            campusMetricsCollector.recordExchange(true, System.currentTimeMillis() - startTime);
        }

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
     * 基于 (student_id, request_id) 联合维度反查已落库的兑换订单。
     * 命中即代表该学生本次兑换意图已经成功处理过，支持网络重试/弱网幂等快路径返回。
     */

    private StudentGift findOrderByRequestId(String requestId, Long studentId) {
        if (!StringUtils.hasText(requestId) || studentId == null) {
            return null;
        }
        // 与本类其余 7 处 giftIdempotentService 调用保持一致：依赖恒非空，直接调用。
        // 这里原本多一层 != null 判空，会在依赖缺失时静默跳过幂等查询并落到 baseMapper 兜底，
        // 造成「同一依赖一处假设非空、一处假设可能为空」的双重标准。
        try {
            StudentGift order = giftIdempotentService.findExistingOrder(studentId, requestId);
            if (order != null) {
                return order;
            }
        } catch (Exception e) {
            log.debug("giftIdempotentService.findExistingOrder 查询异常，降级 baseMapper: {}", e.getMessage());
        }

        return this.getOne(new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getStudentId, studentId)
                .eq(StudentGift::getRequestId, requestId.trim()), false);
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

    /**
     * 生成高并发毫秒级全局唯一业务订单号，复用统一组件 PickupCodeGenerator
     */
    private String generateOrderNo() {
        return pickupCodeGenerator.generateOrderNo();
    }

    /**
     * 生成安全、防碰撞且语义清晰的礼品提货码，复用统一组件 PickupCodeGenerator
     */
    private String generateUniquePickupCode() {
        return pickupCodeGenerator.generateUniquePickupCode(candidate -> {
            Long count = this.baseMapper.selectCount(
                    new LambdaQueryWrapper<StudentGift>().eq(StudentGift::getPickupCode, candidate)
            );
            return count != null && count > 0;
        });
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
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND.getCode(), "未找到对应的兑换订单");
        }
        if (order.getStatus() != OrderStatus.PENDING.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅处于【待发放/待提货】状态的订单支持自主取消！");
        }

        boolean success = doCancelOrder(order, "学生在移动端自主申请取消提货", true, null);
        if (!success) {
            log.warn("学生取消订单未生效，订单状态已被并发操作流转: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅处于【待发放/待提货】状态的订单支持自主取消！");
        }
    }

    @Override
    public Page<StudentGiftVO> getAdminOrderPage(GiftOrderQueryDTO queryDTO) {
        Page<StudentGift> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        LambdaQueryWrapper<StudentGift> wrapper = new LambdaQueryWrapper<StudentGift>()
                .like(StringUtils.hasText(queryDTO.getOrderNo()), StudentGift::getOrderNo, queryDTO.getOrderNo())
                .like(StringUtils.hasText(queryDTO.getPickupCode()), StudentGift::getPickupCode, queryDTO.getPickupCode())
                .eq(queryDTO.getStatus() != null, StudentGift::getStatus, queryDTO.getStatus());

        // 统一处理学生姓名/学号多维度匹配：同时匹配学生账号真实姓名(student.name)、学号(student.student_no)以及订单收件人姓名(student_gift.receiver_name)

        boolean hasNameFilter = StringUtils.hasText(queryDTO.getStudentName());
        boolean hasNoFilter = StringUtils.hasText(queryDTO.getStudentNo());
        if (hasNameFilter || hasNoFilter) {
            String nameKeyword = hasNameFilter ? queryDTO.getStudentName().trim() : null;
            String noKeyword = hasNoFilter ? queryDTO.getStudentNo().trim() : null;

            // 联动 student 表查询真实学生档案

            LambdaQueryWrapper<Student> studentWrapper = new LambdaQueryWrapper<Student>()
                    .select(Student::getId);
            if (nameKeyword != null && noKeyword != null) {
                studentWrapper.and(w -> w.like(Student::getName, nameKeyword).or().like(Student::getStudentNo, noKeyword));
            } else if (nameKeyword != null) {
                studentWrapper.and(w -> w.like(Student::getName, nameKeyword).or().like(Student::getStudentNo, nameKeyword));
            } else {
                studentWrapper.like(Student::getStudentNo, noKeyword);
            }

            List<Long> matchedStudentIds = studentMapper.selectList(studentWrapper).stream()
                    .map(Student::getId)
                    .collect(Collectors.toList());
            wrapper.and(w -> {
                boolean hasCondition = false;
                if (nameKeyword != null) {
                    w.like(StudentGift::getReceiverName, nameKeyword);
                    hasCondition = true;
                }
                if (!matchedStudentIds.isEmpty()) {
                    if (hasCondition) {
                        w.or().in(StudentGift::getStudentId, matchedStudentIds);
                    } else {
                        w.in(StudentGift::getStudentId, matchedStudentIds);
                    }
                } else if (!hasCondition) {
                    // 查询了学号但在学生表未找到匹配记录，返回空结果

                    w.eq(StudentGift::getId, -1L);
                }
            });
        }

        wrapper.orderByDesc(StudentGift::getCreateTime);
        Page<StudentGift> orderPage = this.page(page, wrapper);
        return populateOrderVOs(orderPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDelivery(DeliveryConfirmDTO dto, Long adminId) {
        StudentGift order = this.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND.getCode(), "兑换订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING.getCode()) {
            if (order.getStatus() != null && order.getStatus() == OrderStatus.DELIVERED.getCode()) {
                throw new BusinessException(ResultCode.ORDER_ALREADY_DELIVERED.getCode(), "该订单已核销，不可重复提货");
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该订单状态不是待核销状态，不可重复操作");
        }

        // 严格比对提货码（忽略前后空格与横杠）
        String cleanInput = dto.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        String cleanTarget = order.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        if (!cleanInput.equals(cleanTarget)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货码核销校验不匹配，核销失败！");
        }

        // 状态守卫的条件更新（status 1 -> 2）：并发下只有一方能把订单核销掉
        int changed = this.baseMapper.confirmDeliveryWithStatusGuard(order.getId(), OrderStatus.PENDING.getCode(), LocalDateTime.now(),
                adminId, StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : null);
        if (changed == 0) {
            log.warn("核销未生效，订单状态已被并发操作流转: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            throw new BusinessException(ResultCode.ORDER_ALREADY_DELIVERED.getCode(), "该订单已核销，不可重复提货");
        }

        // 核销通知放到事务提交之后执行，避免事务回滚时误推

        String orderNo = order.getOrderNo();
        Long studentId = order.getStudentId();
        Long orderId = order.getId();
        AfterCommitExecutor.run(() -> {
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
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND.getCode(), "兑换订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅【待发放】订单允许取消退单");
        }

        boolean refund = dto.getRefundPoints() == null || dto.getRefundPoints();
        if (!doCancelOrder(order, dto.getCancelReason(), refund, adminId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅【待发放】订单允许取消退单");
        }

        AfterCommitExecutor.run(() -> {
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
        int changed = this.baseMapper.cancelOrderWithStatusGuard(order.getId(), OrderStatus.PENDING.getCode(), refund ? 1 : 0,
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

                pointLedger.record(order.getStudentId(), com.campus.modules.mall.entity.PointRecordType.ORDER_REFUND.getCode(), order.getPointsCost(), afterPoints,
                        remark, order.getOrderNo(), order.getId(), operatorId);
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

        // 严格幂等防线：只有待提货状态才执行超时自动关单
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING.getCode()) {
            log.info("[延时关单] 订单已非待提货状态(status={})，幂等退出: orderId={}", order.getStatus(), orderId);
            return;
        }

        // 复用取消路径（带状态守卫条件更新）：与学生自主取消/管理员取消并发时只会有一方生效。

        // 受影响行数为 0 时按幂等静默退出、不抛异常，避免 MQ 无限重投。

        boolean cancelled = doCancelOrder(order, "超时未提货，系统自动关单", true, 0L);
        if (cancelled) {
            if (campusMetricsCollector != null) {
                campusMetricsCollector.recordOrderExpired();
            }

            log.info("[延时关单] 订单超时自动关单并退还积分成功: orderId={}, orderNo={}", orderId, order.getOrderNo());
            com.campus.common.tx.AfterCommitExecutor.run(() -> {
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

    @Override
    public GiftPickupQrCodeVO getPickupQrCode(Long orderId, Long studentId) {
        StudentGift order = this.getById(orderId);
        if (order == null || Objects.equals(order.getDeleted(), 1)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND.getCode(), "兑换订单不存在");
        }
        if (!Objects.equals(order.getStudentId(), studentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权查看他人的提货凭据");
        }
        if (Objects.equals(order.getStatus(), OrderStatus.DELIVERED.getCode())) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_DELIVERED.getCode(), "该订单已完成核销提货");
        }
        if (Objects.equals(order.getStatus(), OrderStatus.CANCELLED.getCode())) {
            throw new BusinessException(ResultCode.ORDER_ALREADY_CANCELLED.getCode(), "该订单已被取消");
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "订单状态不支持生成提货二维码");
        }

        long now = System.currentTimeMillis();
        int validSeconds = mallPickupProperties.getValidSeconds();
        String token = pickupTokenService.generateToken(order.getOrderNo(), order.getPickupCode(), now);
        String qrCodeBase64 = QrCodeUtil.generateQrCodeBase64(token, 300, 300);
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(validSeconds);
        Gift gift = giftMapper.selectById(order.getGiftId());
        String giftName = gift != null ? gift.getName() : "兑换礼品";
        return GiftPickupQrCodeVO.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .giftName(giftName)
                .pickupCode(order.getPickupCode())
                .pickupToken(token)
                .qrCodeBase64(qrCodeBase64)
                .expireTime(expireTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .validSeconds(validSeconds)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PickupVerifyResultVO verifyAndDeliverByQrCode(PickupQrVerifyDTO dto, Long adminId) {
        if (dto == null || !StringUtils.hasText(dto.getQrToken())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货二维码内容不能为空");
        }

        // 1. 解码并校验动态防伪 Token（HMAC-SHA256 与有效时长校验）
        PickupTokenService.PickupTokenPayload payload = pickupTokenService.verifyAndParseToken(dto.getQrToken());

        // 2. 根据订单号查询订单实体
        StudentGift order = this.baseMapper.selectOne(new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getOrderNo, payload.getOrderNo())
                .eq(StudentGift::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND.getCode(), "提货订单不存在");
        }

        // 3. 校验订单状态
        if (!Objects.equals(order.getStatus(), OrderStatus.PENDING.getCode())) {
            if (Objects.equals(order.getStatus(), OrderStatus.DELIVERED.getCode())) {
                throw new BusinessException(ResultCode.ORDER_ALREADY_DELIVERED.getCode(), "该订单已完成核销，请勿重复操作");
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该订单状态不是待核销状态，不可提货");
        }

        // 4. 校验提货码是否匹配
        String cleanInput = payload.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        String cleanTarget = order.getPickupCode().replaceAll("[\\s-]", "").toUpperCase();
        if (!cleanInput.equals(cleanTarget)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "提货码核销校验不匹配，核销失败！");
        }

        // 5. 状态守卫原子更新（status 1 -> 2）
        LocalDateTime deliveryTime = LocalDateTime.now();
        int changed = this.baseMapper.confirmDeliveryWithStatusGuard(order.getId(), OrderStatus.PENDING.getCode(), deliveryTime,
                adminId, StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "扫码枪秒级核销");
        if (changed == 0) {
            log.warn("扫码核销未生效，订单状态已被并发操作抢占: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            throw new BusinessException(ResultCode.ORDER_ALREADY_DELIVERED.getCode(), "该订单已被核销，请勿重复提货");
        }

        Gift gift = giftMapper.selectById(order.getGiftId());
        String giftName = gift != null ? gift.getName() : "兑换礼品";

        // 6. 事务提交后向学生发送系统领奖通知

        String orderNo = order.getOrderNo();
        Long studentId = order.getStudentId();
        Long orderId = order.getId();
        AfterCommitExecutor.run(() -> {
            try {
                String title = "🎁 礼品已成功扫码核销领取";
                String content = String.format("您的礼品订单【%s】已通过扫码枪完成秒级核销领奖！", orderNo);
                sysNoticeService.createAndSendNotice(2, studentId, "gift_send", title, content, orderId);
            } catch (Exception e) {
                log.warn("向学生发送扫码核销通知异常: {}", e.getMessage());
            }
        });
        return PickupVerifyResultVO.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .studentId(order.getStudentId())
                .giftName(giftName)
                .quantity(order.getQuantity())
                .pointsCost(order.getPointsCost())
                .deliveryTime(deliveryTime)
                .status(2)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftExchangeVO seckillExchange(GiftExchangeDTO dto, Long studentId) {
        long startTime = System.currentTimeMillis();
        if (dto.getGiftId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "礼品ID不能为空");
        }
        if (studentId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "学生未登录或身份异常");
        }

        String requestId = StringUtils.hasText(dto.getRequestId())
                ? dto.getRequestId().trim()
                : (StringUtils.hasText(dto.getExchangeToken())
                        ? dto.getExchangeToken().trim()
                        : "SEC-" + UUID.randomUUID().toString().replace("-", ""));

        // 1. 幂等快路径检查

        StudentGift existingOrder = findOrderByRequestId(requestId, studentId);
        if (existingOrder != null) {
            return buildExchangeVO(existingOrder);
        }

        // 2. Redis 占位

        if (!giftIdempotentService.tryAcquireSlot(studentId, requestId)) {
            StudentGift doneOrder = findOrderByRequestId(requestId, studentId);
            if (doneOrder != null) {
                return buildExchangeVO(doneOrder);
            }

            throw new BusinessException(ResultCode.CONFLICT.getCode(), "当前秒杀请求正在处理中，请勿重复提交！");
        }

        // 3. 防重令牌核验

        String tokenToVerify = StringUtils.hasText(dto.getExchangeToken())
                ? dto.getExchangeToken().trim()
                : (StringUtils.hasText(dto.getRequestId()) && dto.getRequestId().startsWith("EXT-")
                        ? dto.getRequestId().trim()
                        : null);
        if (StringUtils.hasText(tokenToVerify)) {
            boolean tokenValid = giftIdempotentService.verifyAndConsumeToken(studentId, dto.getGiftId(), tokenToVerify);
            if (!tokenValid) {
                throw new BusinessException(ResultCode.EXCHANGE_TOKEN_INVALID.getCode(), "兑换令牌已失效或已被使用，请重新进入详情页获取最新兑换令牌！");
            }
        }

        // 4. 学生状态校验（统一口径）
        Student student = requireExchangeableStudent(studentId);

        // 5. 礼品校验 (走缓存或查库)
        Gift gift = giftCacheService != null ? giftCacheService.getGiftDetail(dto.getGiftId()) : requireOnShelfGift(dto.getGiftId());
        if (gift == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "您选择的礼品不存在");
        }
        if (gift.getStatus() == null || gift.getStatus() != GiftStatus.ON_SHELF.getCode()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该礼品已暂时下架");
        }

        int quantity = (dto.getQuantity() != null && dto.getQuantity() > 0) ? dto.getQuantity() : 1;
        int totalPointsCost = gift.getPointsRequired() * quantity;
        int currentBalance = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (currentBalance < totalPointsCost) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH.getCode(), String.format("可用积分不足！当前余额: %d 积分，本次兑换需要: %d 积分", currentBalance, totalPointsCost));
        }

        // 6. Redis + Lua 原子预扣库存 (P1-1 阻断 99% 的无效流量打入数据库)
        //    预扣成功后立即注册回滚补偿：Redis DECRBY 不参与本地事务，
        //    若后续发件箱落库/事务提交失败，必须回补，否则这笔库存被永久占用（少卖）。
        //    messageId 预先生成，回滚补偿也走一次性幂等脚本，避免异常回调重复加库存。

        String messageId = UUID.randomUUID().toString();

        if (giftStockRedisService != null) {
            boolean preDeductOk = giftStockRedisService.preDeductStock(gift.getId(), quantity);
            if (!preDeductOk) {
                if (campusMetricsCollector != null) {
                    campusMetricsCollector.recordExchange(false, System.currentTimeMillis() - startTime);
                }

                throw new BusinessException(ResultCode.GIFT_STOCK_EMPTY.getCode(), "礼品库存已被抢光！");
            }

            final Long preDeductGiftId = gift.getId();
            final int preDeductQuantity = quantity;
            final String compensationMessageId = messageId;
            AfterCommitExecutor.compensateOnRollback(
                    () -> giftStockRedisService.compensateStockOnce(
                            preDeductGiftId, preDeductQuantity, compensationMessageId));
        }

        // 7. 构造强类型消息契约与本地发件箱持久化 (P1-2 & P1-3)

        String orderNo = generateOrderNo();
        ReceiverInfo receiver = resolveReceiver(dto, student);
        OrderCreateMessage msg = new OrderCreateMessage(
                messageId,
                studentId,
                gift.getId(),
                quantity,
                requestId,
                tokenToVerify,
                receiver.name(),
                receiver.phone(),
                receiver.address(),
                System.currentTimeMillis(),
                MDC.get("traceId"),
                orderNo
        );
        // 发件箱落库失败必须让事务回滚（不再静默跳过）：否则 Redis 已预扣却无任何补偿凭据。
        // localMessageService 在生产环境恒非空（Spring 必注册），判空分支仅为短构造器单测保留；
        // 一旦为 null 就抛异常让整笔兑换回滚，绝不产生「预扣了却无人记账」的少卖。
        if (localMessageService == null) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "秒杀通道未正确装配，请联系管理员！");
        }
        localMessageService.savePendingMessage(messageId, "GIFT_ORDER_CREATE", orderNo, msg, MDC.get("traceId"));

        // 8. 投递 MQ 削峰队列（提交后执行，避免事务回滚时消息已发出；瞬时投递失败不阻断，发件箱定时任务自动补偿）
        AfterCommitExecutor.run(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                        RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                        msg
                );
                localMessageService.markPublished(messageId);
            } catch (Exception e) {
                log.warn("[秒杀下单] MQ 提交后投递异常，将由本地发件箱定时任务自动补偿重试: messageId={}", messageId, e);
            }
        });
        if (campusMetricsCollector != null) {
            campusMetricsCollector.recordExchange(true, System.currentTimeMillis() - startTime);
        }

        // 9. 毫秒级返回排队处理结果

        GiftExchangeVO vo = new GiftExchangeVO();
        vo.setOrderNo(orderNo);
        vo.setGiftName(gift.getName());
        vo.setQuantity(quantity);
        vo.setPointsCost(totalPointsCost);
        vo.setRemainPoints(currentBalance - totalPointsCost);
        vo.setPickupCode("QUEUED");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean processAsyncOrder(OrderCreateMessage msg) {
        StudentGift existing = findOrderByRequestId(msg.requestId(), msg.studentId());
        if (existing != null) {
            log.info("[异步落库] 订单已落库(幂等命中): orderNo={}", existing.getOrderNo());
            return true;
        }

        Student student = studentMapper.selectById(msg.studentId());
        if (student == null || (student.getStatus() != null && student.getStatus() == StudentStatus.DISABLED.getCode())) {
            log.warn("[异步落库] 学生账号异常或冻结: studentId={}", msg.studentId());
            return false;
        }

        Gift gift = giftMapper.selectById(msg.giftId());
        if (gift == null || (gift.getDeleted() != null && gift.getDeleted() == 1)) {
            log.warn("[异步落库] 礼品不存在: giftId={}", msg.giftId());
            return false;
        }

        int totalPointsCost = gift.getPointsRequired() * msg.quantity();
        int currentBalance = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (currentBalance < totalPointsCost) {
            log.warn("[异步落库] 可用积分不足: currentBalance={}, cost={}", currentBalance, totalPointsCost);
            return false;
        }

        // 条件乐观锁扣减 MySQL 库存 (重试 3 次)
        int updatedStock = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            updatedStock = giftMapper.reduceStockWithOptimisticLock(gift.getId(), msg.quantity(), gift.getVersion());
            if (updatedStock > 0) break;
            gift = giftMapper.selectById(msg.giftId());
            if (gift == null || gift.getStock() < msg.quantity()) break;
        }
        if (updatedStock == 0) {
            log.warn("[异步落库] 乐观锁更新数据库库存失败: giftId={}", msg.giftId());
            return false;
        }

        int updatedPoints = studentMapper.deductCurrentPoints(student.getId(), totalPointsCost);
        if (updatedPoints == 0) {
            throw new BusinessException(ResultCode.POINTS_NOT_ENOUGH.getCode(), "可用积分扣减失败，余额不足");
        }
        int afterPoints = studentMapper.selectCurrentPoints(student.getId());
        String pickupCode = generateUniquePickupCode();
        StudentGift order = new StudentGift();
        // 复用入口已生成并随消息投递、写入发件箱与返回给客户端的同一订单号，保留旧在途消息兜底
        order.setOrderNo(StringUtils.hasText(msg.orderNo()) ? msg.orderNo() : generateOrderNo());
        order.setStudentId(msg.studentId());
        order.setGiftId(gift.getId());
        order.setQuantity(msg.quantity());
        order.setPointsCost(totalPointsCost);
        order.setRequestId(msg.requestId());
        order.setReceiverName(msg.receiverName());
        order.setReceiverPhone(msg.receiverPhone());
        order.setReceiverAddress(msg.receiverAddress());
        order.setPickupCode(pickupCode);
        order.setStatus(OrderStatus.PENDING);
        order.setRefundPointsStatus(0);
        this.save(order);
        pointLedger.record(
                student.getId(),
                com.campus.modules.mall.entity.PointRecordType.GIFT_EXCHANGE.getCode(),
                -totalPointsCost,
                afterPoints,
                String.format("秒杀兑换礼品【%s】x %d 件 (提货码: %s)", gift.getName(), msg.quantity(), pickupCode),
                order.getOrderNo(),
                order.getId(),
                null
        );
        Long orderId = order.getId();
        String orderNo = order.getOrderNo();
        AfterCommitExecutor.run(() -> {
            giftIdempotentService.markSlotDone(msg.studentId(), msg.requestId(), orderId);
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.GIFT_ORDER_EXCHANGE,
                        RabbitMqConfig.GIFT_ORDER_DELAY_ROUTING_KEY,
                        Map.of("orderId", orderId, "pickupCode", pickupCode, "studentId", msg.studentId()));
            } catch (Exception e) {
                log.warn("秒杀订单延时关单消息投递异常: orderId={}", orderId, e);
            }
        });
        log.info("[异步落库] 秒杀订单成功落库: orderNo={}, studentId={}, orderId={}", orderNo, msg.studentId(), orderId);
        return true;
    }

    @Override
    public StudentGiftVO getSeckillResultByRequestId(String requestId, Long studentId) {
        if (!StringUtils.hasText(requestId) || studentId == null) {
            return null;
        }
        StudentGift order = this.getOne(new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getRequestId, requestId.trim())
                .eq(StudentGift::getStudentId, studentId)
                .last("LIMIT 1"));
        if (order == null) {
            return null;
        }
        StudentGiftVO vo = new StudentGiftVO();
        org.springframework.beans.BeanUtils.copyProperties(order, vo);
        return vo;
    }

    @Override
    public boolean existsOrderByRequestId(Long studentId, String requestId) {
        return findOrderByRequestId(requestId, studentId) != null;
    }
}
