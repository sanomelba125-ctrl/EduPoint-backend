package com.campus.modules.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.dto.LedgerMetricsRow;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.student.dto.*;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.student.service.StudentScoreService;
import com.campus.modules.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 积分台账与学分光荣榜业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentScoreServiceImpl implements StudentScoreService {

    private final StudentMapper studentMapper;
    private final StudentPointRecordMapper pointRecordMapper;
    private final PointLedger pointLedger;
    private final SysNoticeService sysNoticeService;

    @Override
    public StudentScoreBalanceVO getBalance(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生档案不存在");
        }

        int current = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        int total = student.getTotalPoints() != null ? student.getTotalPoints() : 0;
        int rank = studentMapper.calculateStudentRank(total, student.getStudentNo());

        return StudentScoreBalanceVO.builder()
                .currentPoints(current)
                .totalPoints(total)
                .rank(rank)
                .build();
    }

    @Override
    public Page<StudentPointRecordVO> getMyPointRecords(Integer page, Integer size, Integer type, Long studentId) {
        Page<StudentPointRecord> queryPage = new Page<>(page != null ? page : 1, size != null ? size : 20);

        LambdaQueryWrapper<StudentPointRecord> wrapper = new LambdaQueryWrapper<StudentPointRecord>()
                .eq(StudentPointRecord::getStudentId, studentId);

        // 筛选类型处理：
        // 1: 打卡奖励(+)
        // 2: 商城兑换(-)
        // 3: 管理调账(±)
        // 4: 订单取消返还(+)
        // 100: 全部收入 (type IN 1, 4, 或 change_points > 0)
        // 200: 全部支出 (type = 2 或 change_points < 0)
        if (type != null) {
            if (type == 100) {
                wrapper.gt(StudentPointRecord::getChangePoints, 0);
            } else if (type == 200) {
                wrapper.lt(StudentPointRecord::getChangePoints, 0);
            } else {
                wrapper.eq(StudentPointRecord::getType, type);
            }
        }

        wrapper.orderByDesc(StudentPointRecord::getCreateTime)
               .orderByDesc(StudentPointRecord::getId);

        Page<StudentPointRecord> recordPage = pointRecordMapper.selectPage(queryPage, wrapper);
        return convertToVOPage(recordPage);
    }

    @Override
    public StudentRankResultVO getLeaderboard(Integer page, Integer size, String college, Long currentStudentId) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 20;

        Page<Student> queryPage = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>()
                .eq(Student::getDeleted, 0)
                .eq(StringUtils.hasText(college), Student::getCollege, college)
                .orderByDesc(Student::getTotalPoints)
                .orderByAsc(Student::getStudentNo);

        Page<Student> studentPage = studentMapper.selectPage(queryPage, wrapper);
        List<Student> records = studentPage.getRecords();

        List<RankStudentVO> voList = new ArrayList<>();
        int startIndex = (pageNum - 1) * pageSize;

        for (int i = 0; i < records.size(); i++) {
            Student s = records.get(i);
            int rankNumber = startIndex + i + 1;
            boolean isMe = currentStudentId != null && currentStudentId.equals(s.getId());

            RankStudentVO vo = RankStudentVO.builder()
                    .rank(rankNumber)
                    .studentId(s.getId())
                    .studentNo(s.getStudentNo())
                    .name(s.getName())
                    .avatar(s.getAvatar())
                    .college(s.getCollege())
                    .className(s.getClassName())
                    .totalPoints(s.getTotalPoints() != null ? s.getTotalPoints() : 0)
                    .currentPoints(s.getCurrentPoints() != null ? s.getCurrentPoints() : 0)
                    .isMe(isMe)
                    .build();
            voList.add(vo);
        }

        // 当前学生自身位次与分差计算
        RankStudentVO myRankVO = null;
        Integer diffToPrevious = 0;
        String previousStudentName = "";

        if (currentStudentId != null) {
            Student me = studentMapper.selectById(currentStudentId);
            if (me != null) {
                int myTotal = me.getTotalPoints() != null ? me.getTotalPoints() : 0;
                int exactRank = studentMapper.calculateStudentRank(myTotal, me.getStudentNo());

                myRankVO = RankStudentVO.builder()
                        .rank(exactRank)
                        .studentId(me.getId())
                        .studentNo(me.getStudentNo())
                        .name(me.getName())
                        .avatar(me.getAvatar())
                        .college(me.getCollege())
                        .className(me.getClassName())
                        .totalPoints(myTotal)
                        .currentPoints(me.getCurrentPoints() != null ? me.getCurrentPoints() : 0)
                        .isMe(true)
                        .build();

                // 若非第一名，查找前一名同学以便计算差距分
                if (exactRank > 1) {
                    Page<Student> prevPage = new Page<>(exactRank - 1, 1);
                    Page<Student> prevResult = studentMapper.selectPage(prevPage,
                            new LambdaQueryWrapper<Student>()
                                    .eq(Student::getDeleted, 0)
                                    .orderByDesc(Student::getTotalPoints)
                                    .orderByAsc(Student::getStudentNo));
                    if (!prevResult.getRecords().isEmpty()) {
                        Student prevStudent = prevResult.getRecords().get(0);
                        previousStudentName = prevStudent.getName();
                        int prevTotal = prevStudent.getTotalPoints() != null ? prevStudent.getTotalPoints() : 0;
                        diffToPrevious = Math.max(0, prevTotal - myTotal);
                    }
                }
            }
        }

        return StudentRankResultVO.builder()
                .records(voList)
                .total(studentPage.getTotal())
                .myRank(myRankVO)
                .diffToPrevious(diffToPrevious)
                .previousStudentName(previousStudentName)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustPoints(PointAdjustDTO dto, Long adminId) {
        Student student = studentMapper.selectById(dto.getStudentId());
        if (student == null || (student.getDeleted() != null && student.getDeleted() == 1)) {
            throw new BusinessException("目标学生学籍档案不存在");
        }

        int beforeCurrent = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        int change = dto.getPoints();

        // 负数扣除强防穿透校验
        if (change < 0) {
            int deduct = Math.abs(change);
            if (beforeCurrent < deduct) {
                throw new BusinessException(String.format("扣减后可用积分将变负数，系统拦截操作！学生当前余额仅剩: %d pts", beforeCurrent));
            }
            int rows = studentMapper.deductCurrentPoints(student.getId(), deduct);
            if (rows == 0) {
                throw new BusinessException("扣减可用积分失败，并发冲突或余额不足");
            }
        } else if (change > 0) {
            // 正数增加：可用积分与历史累计积分同步增加
            studentMapper.addCurrentPoints(student.getId(), change);
            studentMapper.addTotalPoints(student.getId(), change);
        } else {
            throw new BusinessException("调账分值不能为 0");
        }

        int afterCurrent = beforeCurrent + change;

        // 写入财务级对账流水 (通过 PointLedger 统一落账)
        String remark = dto.getReason();
        if (StringUtils.hasText(dto.getApprovalNo())) {
            remark = String.format("调账审批单【%s】: %s", dto.getApprovalNo(), remark);
        }
        pointLedger.record(student.getId(), 3, change, afterCurrent, remark, null, adminId);

        // 保存系统通知并触发 WebSocket 实时推送
        try {
            Map<String, Object> extraPayload = new HashMap<>();
            extraPayload.put("change", change);
            extraPayload.put("currentPoints", afterCurrent);
            extraPayload.put("reason", dto.getReason());

            String title = "系统积分调账通知";
            String content = String.format("您的账户积分变动 %+d，当前可用余额 %d 分。调账原因：%s", change, afterCurrent, dto.getReason());
            sysNoticeService.createAndSendNotice(2, student.getId(), "points_adjusted", title, content, null, extraPayload);
        } catch (Exception e) {
            log.warn("保存与推送调账通知失败: {}", e.getMessage());
        }
    }

    @Override
    public Page<StudentPointRecordVO> getAdminRecordPage(AdminPointRecordQueryDTO queryDTO) {
        Page<StudentPointRecord> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());

        LambdaQueryWrapper<StudentPointRecord> wrapper = new LambdaQueryWrapper<StudentPointRecord>()
                .eq(queryDTO.getType() != null, StudentPointRecord::getType, queryDTO.getType())
                .orderByDesc(StudentPointRecord::getCreateTime)
                .orderByDesc(StudentPointRecord::getId);

        // 如果传了学生姓名或学号或综合关键字，先匹配出符合条件的学生 ID 列表
        if (StringUtils.hasText(queryDTO.getKeyword()) || StringUtils.hasText(queryDTO.getStudentNo()) || StringUtils.hasText(queryDTO.getStudentName())) {
            LambdaQueryWrapper<Student> stuWrapper = new LambdaQueryWrapper<Student>()
                    .select(Student::getId);

            if (StringUtils.hasText(queryDTO.getKeyword())) {
                String kw = queryDTO.getKeyword().trim();
                stuWrapper.and(w -> w.like(Student::getStudentNo, kw)
                        .or().like(Student::getName, kw));
            }
            if (StringUtils.hasText(queryDTO.getStudentNo())) {
                stuWrapper.like(Student::getStudentNo, queryDTO.getStudentNo().trim());
            }
            if (StringUtils.hasText(queryDTO.getStudentName())) {
                stuWrapper.like(Student::getName, queryDTO.getStudentName().trim());
            }

            List<Long> matchedIds = studentMapper.selectList(stuWrapper).stream()
                    .map(Student::getId)
                    .collect(Collectors.toList());

            if (matchedIds.isEmpty()) {
                Page<StudentPointRecordVO> empty = new Page<>(page.getCurrent(), page.getSize(), 0);
                empty.setRecords(Collections.emptyList());
                return empty;
            }

            wrapper.in(StudentPointRecord::getStudentId, matchedIds);
        }

        Page<StudentPointRecord> recordPage = pointRecordMapper.selectPage(page, wrapper);
        return convertToVOPage(recordPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rewardActivityCheckin(Long studentId, Long studentActivityId, String activityTitle, Integer pointsAwarded) {
        if (studentId == null) {
            throw new BusinessException("打卡学生 ID 不能为空");
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null || (student.getDeleted() != null && student.getDeleted() == 1)) {
            throw new BusinessException("目标学生学籍档案不存在或已注销");
        }

        int beforeCurrent = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        if (pointsAwarded == null || pointsAwarded <= 0) {
            log.warn("活动打卡积分奖励小于等于 0，跳过入账: studentId={}, points={}", studentId, pointsAwarded);
            return beforeCurrent;
        }

        // 1. 原子增加可用积分与历史累计积分
        studentMapper.addCurrentPoints(studentId, pointsAwarded);
        studentMapper.addTotalPoints(studentId, pointsAwarded);

        Integer currentDb = studentMapper.selectCurrentPoints(studentId);
        int afterCurrent = currentDb != null ? currentDb : (beforeCurrent + pointsAwarded);

        // 2. 写入财务级对账流水 (通过 PointLedger 统一落账)
        String actTitle = StringUtils.hasText(activityTitle) ? activityTitle.trim() : "校园活动";
        pointLedger.record(studentId, 1, pointsAwarded, afterCurrent,
                String.format("参与活动打卡奖励【%s】", actTitle), studentActivityId, 0L);

        // 3. 保存系统通知并触发 WebSocket 实时推送
        try {
            Map<String, Object> extraPayload = new HashMap<>();
            extraPayload.put("activityTitle", actTitle);
            extraPayload.put("pointsAwarded", pointsAwarded);
            extraPayload.put("currentPoints", afterCurrent);

            String title = "🎉 活动打卡奖励到账";
            String content = String.format("参与活动【%s】打卡成功！奖励到账：+%d 分，当前可用余额：%d 分", actTitle, pointsAwarded, afterCurrent);
            sysNoticeService.createAndSendNotice(2, studentId, "activity_checkin_reward", title, content, studentActivityId, extraPayload);
        } catch (Exception e) {
            log.warn("保存与推送打卡奖励通知失败: {}", e.getMessage());
        }

        return afterCurrent;
    }

    @Override
    public LedgerMetricsVO getLedgerMetrics() {
        // 单条 SQL 数据库聚合统计，消除将全量流水拉入 JVM 的内存与 CPU 瓶颈
        LedgerMetricsRow row = pointRecordMapper.selectLedgerMetrics();
        if (row == null) {
            return LedgerMetricsVO.builder()
                    .totalEarnedPoints(0)
                    .totalSpentPoints(0)
                    .totalRefundedPoints(0)
                    .adjustCount(0)
                    .build();
        }
        return LedgerMetricsVO.builder()
                .totalEarnedPoints(row.getEarned() != null ? row.getEarned().intValue() : 0)
                .totalSpentPoints(row.getSpent() != null ? row.getSpent().intValue() : 0)
                .totalRefundedPoints(row.getRefunded() != null ? row.getRefunded().intValue() : 0)
                .adjustCount(row.getAdjustCount() != null ? row.getAdjustCount().intValue() : 0)
                .build();
    }

    private Page<StudentPointRecordVO> convertToVOPage(Page<StudentPointRecord> recordPage) {
        List<StudentPointRecord> records = recordPage.getRecords();
        if (records.isEmpty()) {
            Page<StudentPointRecordVO> empty = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
            empty.setRecords(Collections.emptyList());
            return empty;
        }

        Set<Long> studentIds = records.stream().map(StudentPointRecord::getStudentId).collect(Collectors.toSet());
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");

        List<StudentPointRecordVO> voList = records.stream().map(r -> {
            Student s = studentMap.get(r.getStudentId());
            String typeDesc = switch (r.getType() != null ? r.getType() : 0) {
                case 1 -> "活动打卡奖励";
                case 2 -> "商城兑换消费";
                case 3 -> "系统人工调账";
                case 4 -> "订单取消返还";
                default -> "其他流水变动";
            };

            String businessType = switch (r.getType() != null ? r.getType() : 0) {
                case 1 -> "ACTIVITY";
                case 2, 4 -> "GIFT";
                default -> "SYSTEM";
            };

            String dateStr = r.getCreateTime() != null ? r.getCreateTime().format(dtf) : "20260908";
            String recordNo = "PT-" + dateStr + "-" + String.format("%04d", r.getId());

            String businessId;
            if (r.getRefId() != null) {
                if (r.getType() != null && r.getType() == 1) {
                    businessId = "ACT-" + String.format("%04d", r.getRefId());
                } else if (r.getType() != null && (r.getType() == 2 || r.getType() == 4)) {
                    businessId = "ORD-" + String.format("%04d", r.getRefId());
                } else {
                    businessId = "SYS-" + String.format("%04d", r.getRefId());
                }
            } else {
                businessId = "OA-" + String.format("%04d", r.getId());
            }

            String operatorName = r.getOperatorId() != null && r.getOperatorId() > 0 ? "管理员#" + r.getOperatorId() : "系统自动触发";

            return StudentPointRecordVO.builder()
                    .id(r.getId())
                    .recordNo(recordNo)
                    .studentId(r.getStudentId())
                    .studentNo(s != null ? s.getStudentNo() : "-")
                    .studentName(s != null ? s.getName() : "学生")
                    .college(s != null ? s.getCollege() : "-")
                    .className(s != null ? s.getClassName() : "-")
                    .type(r.getType())
                    .typeDesc(typeDesc)
                    .changePoints(r.getChangePoints())
                    .beforePoints(r.getBeforePoints())
                    .afterPoints(r.getAfterPoints())
                    .description(r.getDescription())
                    .refId(r.getRefId())
                    .businessType(businessType)
                    .businessId(businessId)
                    .operatorId(r.getOperatorId())
                    .operatorName(operatorName)
                    .createTime(r.getCreateTime())
                    .build();
        }).collect(Collectors.toList());

        Page<StudentPointRecordVO> resultPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }
}
