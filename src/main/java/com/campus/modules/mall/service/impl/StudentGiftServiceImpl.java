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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 礼品兑换订单业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentGiftServiceImpl extends ServiceImpl<StudentGiftMapper, StudentGift> implements StudentGiftService {

    private final GiftMapper giftMapper;
    private final StudentMapper studentMapper;
    private final PointLedger pointLedger;
    private final SysNoticeService sysNoticeService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GiftExchangeVO exchangeGift(GiftExchangeDTO dto, Long studentId) {
        // 1. 查询并校验学生账户状态
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

        // 3. 校验学生可用积分余额
        int currentBalance = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (currentBalance < totalPointsCost) {
            throw new BusinessException(String.format("可用积分不足！当前余额: %d pts，本次兑换需要: %d pts", currentBalance, totalPointsCost));
        }

        // 4. 校验库存并执行原子乐观锁扣减（防高并发超卖）
        if (gift.getStock() == null || gift.getStock() < quantity) {
            throw new BusinessException(String.format("礼品可用库存不足！当前仅剩 %d 件", gift.getStock() != null ? gift.getStock() : 0));
        }

        int updatedStock = giftMapper.reduceStockWithOptimisticLock(gift.getId(), quantity, gift.getVersion());
        if (updatedStock == 0) {
            log.warn("礼品兑换触发乐观锁冲突, giftId: {}, version: {}", gift.getId(), gift.getVersion());
            throw new BusinessException("当前抢兑人数较多导致冲突，请刷新后重试！");
        }

        // 5. 原子扣减学生可用积分（total_points 累计积分保持不变，确保光荣榜排名不掉）
        int updatedPoints = studentMapper.deductCurrentPoints(student.getId(), totalPointsCost);
        if (updatedPoints == 0) {
            throw new BusinessException("可用积分扣减失败，余额不足！");
        }
        int afterPoints = studentMapper.selectCurrentPoints(student.getId());

        // 6. 生成全局唯一订单号与提货码
        String orderNo = "ORD" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%04d", (int) (Math.random() * 10000));
        String pickupCode = "PK" + String.format("%04d", (int) (Math.random() * 10000))
                + String.format("%02d", (int) (Math.random() * 100));

        // 7. 保存兑换订单
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
        order.setReceiverName(rName);
        order.setReceiverPhone(rPhone);
        order.setReceiverAddress(rAddress);
        order.setPickupCode(pickupCode);
        order.setStatus(1); // 1-待发放/待提货
        order.setRefundPointsStatus(0);
        this.save(order);

        // 8. 记录财务级积分对账台账 (由 PointLedger 统一推导 before/after 并落账)
        pointLedger.record(studentId, 2, -totalPointsCost, afterPoints,
                String.format("商城兑换礼品【%s】x %d 件 (提货码: %s)", gift.getName(), quantity, pickupCode),
                order.getId(), null);

        // 投递延时死信关单消息 (超时未提货由死信队列自动关单并原路返还积分与库存)
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.GIFT_ORDER_EXCHANGE,
                    RabbitMqConfig.GIFT_ORDER_DELAY_ROUTING_KEY,
                    Map.of("orderId", order.getId(), "pickupCode", pickupCode, "studentId", studentId));
            log.info("礼品订单延时关单消息投递成功: orderId={}, orderNo={}", order.getId(), orderNo);
        } catch (Exception e) {
            log.warn("礼品订单延时关单消息投递失败: orderId={}, error={}", order.getId(), e.getMessage());
        }

        // 9. 保存系统通知并触发 WebSocket 通知推送给在线管理端与学生端
        try {
            Map<String, Object> adminPayload = new HashMap<>();
            adminPayload.put("orderNo", orderNo);
            adminPayload.put("pickupCode", pickupCode);
            adminPayload.put("time", LocalDateTime.now().toString());

            String adminTitle = "新礼品兑换订单";
            String adminContent = String.format("学生 %s 兑换了【%s】x %d 件，订单号: %s", student.getName(), gift.getName(), quantity, orderNo);
            sysNoticeService.createAndSendNotice(1, 0L, "gift_exchange", adminTitle, adminContent, order.getId(), adminPayload);

            // 同时记录学生端通知
            String stuTitle = "🎁 礼品兑换成功";
            String stuContent = String.format("您已成功兑换【%s】x %d 件，消耗 %d 积分，提货码：%s", gift.getName(), quantity, totalPointsCost, pickupCode);
            sysNoticeService.createAndSendNotice(2, studentId, "gift_exchange", stuTitle, stuContent, order.getId());
        } catch (Exception e) {
            log.warn("发送与记录礼品兑换通知失败: {}", e.getMessage());
        }

        return GiftExchangeVO.builder()
                .orderNo(orderNo)
                .pickupCode(pickupCode)
                .pointsCost(totalPointsCost)
                .remainPoints(afterPoints)
                .giftName(gift.getName())
                .quantity(quantity)
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

        // 1. 更新订单状态为已取消
        order.setStatus(3); // 3-已取消
        order.setRefundPointsStatus(1); // 1-已全额退还
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason("学生在移动端自主申请取消提货");
        this.updateById(order);

        // 2. 原路恢复礼品可用库存
        giftMapper.increaseStock(order.getGiftId(), order.getQuantity());

        // 3. 返还积分并读回真实余额
        studentMapper.addCurrentPoints(studentId, order.getPointsCost());
        int afterPoints = studentMapper.selectCurrentPoints(studentId);

        // 4. 记入积分流水台账 (由 PointLedger 统一推导 before/after 并落账)
        pointLedger.record(studentId, 4, order.getPointsCost(), afterPoints,
                String.format("兑换订单【%s】取消，原路返还积分", order.getOrderNo()),
                order.getId(), null);
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

        order.setStatus(2); // 2-已送达/已核销
        order.setDeliveryTime(LocalDateTime.now());
        order.setOperatorId(adminId);
        if (StringUtils.hasText(dto.getRemark())) {
            order.setRemark(dto.getRemark());
        }
        this.updateById(order);

        // 保存系统通知并推送给学生端
        try {
            String title = "🎁 礼品已成功核销领取";
            String content = String.format("您的礼品订单【%s】已完成核销领奖！", order.getOrderNo());
            sysNoticeService.createAndSendNotice(2, order.getStudentId(), "gift_send", title, content, order.getId());
        } catch (Exception e) {
            log.warn("向学生发送核销通知异常: {}", e.getMessage());
        }
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

        order.setStatus(3); // 3-已取消
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(dto.getCancelReason() != null ? dto.getCancelReason() : "管理员审核驳回取消");
        order.setOperatorId(adminId);

        boolean refund = dto.getRefundPoints() == null || dto.getRefundPoints();
        order.setRefundPointsStatus(refund ? 1 : 0);
        this.updateById(order);

        // 恢复库存
        giftMapper.increaseStock(order.getGiftId(), order.getQuantity());

        // 原路退还积分
        if (refund) {
            studentMapper.addCurrentPoints(order.getStudentId(), order.getPointsCost());
            int afterPoints = studentMapper.selectCurrentPoints(order.getStudentId());

            pointLedger.record(order.getStudentId(), 4, order.getPointsCost(), afterPoints,
                    String.format("管理员取消订单【%s】，全额退还积分", order.getOrderNo()),
                    order.getId(), adminId);
        }
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

        OrderCancelDTO cancelDTO = new OrderCancelDTO();
        cancelDTO.setOrderId(orderId);
        cancelDTO.setCancelReason("超时未提货，系统自动关单");
        cancelDTO.setRefundPoints(true);
        this.adminCancelOrder(cancelDTO, 0L);
        log.info("[延时关单] 订单超时自动关单并退还积分成功: orderId={}, orderNo={}", orderId, order.getOrderNo());
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
}
