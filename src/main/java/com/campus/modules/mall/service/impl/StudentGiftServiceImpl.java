package com.campus.modules.mall.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.websocket.WebSocketServer;
import com.campus.modules.mall.dto.*;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.StudentGiftService;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final StudentPointRecordMapper pointRecordMapper;

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

        // 6. 生成全局唯一订单号与提货码
        String orderNo = "ORD" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%04d", (int) (Math.random() * 10000));
        String pickupCode = "PK" + String.format("%04d", (int) (Math.random() * 10000))
                + String.format("%02d", (int) (Math.random() * 100));

        // 7. 保存兑换订单
        StudentGift order = new StudentGift();
        order.setOrderNo(orderNo);
        order.setStudentId(studentId);
        order.setGiftId(gift.getId());
        order.setQuantity(quantity);
        order.setPointsCost(totalPointsCost);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setPickupCode(pickupCode);
        order.setStatus(1); // 1-待发放/待提货
        order.setRefundPointsStatus(0);
        this.save(order);

        // 8. 记录财务级积分对账台账
        StudentPointRecord record = new StudentPointRecord();
        record.setStudentId(studentId);
        record.setType(2); // 2-商城兑换消耗(-)
        record.setChangePoints(-totalPointsCost);
        record.setBeforePoints(currentBalance);
        record.setAfterPoints(currentBalance - totalPointsCost);
        record.setDescription(String.format("商城兑换礼品【%s】x %d 件 (提货码: %s)", gift.getName(), quantity, pickupCode));
        record.setRefId(order.getId());
        pointRecordMapper.insert(record);

        // 9. 触发 WebSocket 通知推送给在线管理端
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "gift_exchange");
            msg.put("title", "新礼品兑换订单");
            msg.put("content", String.format("学生 %s 兑换了【%s】x %d 件", student.getName(), gift.getName(), quantity));
            msg.put("orderNo", orderNo);
            msg.put("pickupCode", pickupCode);
            msg.put("time", LocalDateTime.now().toString());
            WebSocketServer.broadcastToUserType("admin", msg.toJSONString());
        } catch (Exception e) {
            log.warn("发送管理员实时兑换通知失败: {}", e.getMessage());
        }

        return GiftExchangeVO.builder()
                .orderNo(orderNo)
                .pickupCode(pickupCode)
                .pointsCost(totalPointsCost)
                .remainPoints(currentBalance - totalPointsCost)
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

        // 3. 查验学生当前余额并返还积分
        Student student = studentMapper.selectById(studentId);
        int beforePoints = student != null && student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        studentMapper.addCurrentPoints(studentId, order.getPointsCost());

        // 4. 记入积分流水台账
        StudentPointRecord record = new StudentPointRecord();
        record.setStudentId(studentId);
        record.setType(4); // 4-订单取消返还(+)
        record.setChangePoints(order.getPointsCost());
        record.setBeforePoints(beforePoints);
        record.setAfterPoints(beforePoints + order.getPointsCost());
        record.setDescription(String.format("兑换订单【%s】取消，原路返还积分", order.getOrderNo()));
        record.setRefId(order.getId());
        pointRecordMapper.insert(record);
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

        // 推送给学生端
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "gift_send");
            msg.put("title", "礼品已成功核销领取");
            msg.put("content", String.format("您的礼品订单【%s】已完成核销领奖！", order.getOrderNo()));
            WebSocketServer.sendInfo("student", order.getStudentId().toString(), msg.toJSONString());
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
            Student student = studentMapper.selectById(order.getStudentId());
            int beforePoints = student != null && student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
            studentMapper.addCurrentPoints(order.getStudentId(), order.getPointsCost());

            StudentPointRecord record = new StudentPointRecord();
            record.setStudentId(order.getStudentId());
            record.setType(4); // 4-订单取消返还(+)
            record.setChangePoints(order.getPointsCost());
            record.setBeforePoints(beforePoints);
            record.setAfterPoints(beforePoints + order.getPointsCost());
            record.setDescription(String.format("管理员取消订单【%s】，全额退还积分", order.getOrderNo()));
            record.setRefId(order.getId());
            record.setOperatorId(adminId);
            pointRecordMapper.insert(record);
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
}
