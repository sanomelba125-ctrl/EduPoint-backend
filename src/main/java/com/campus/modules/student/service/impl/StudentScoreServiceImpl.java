package com.campus.modules.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.dto.LedgerMetricsRow;
import com.campus.modules.mall.entity.PointRecordType;
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
 * 积分台账与学分光荣榜业务实现。
 *
 * <p>本类承载积分账户域的全部读写逻辑，有三条贯穿始终的设计约束，改动前务必先读懂：
 *
 * <h3>一、双积分模型：两个字段语义不可混用</h3>
 * <ul>
 *   <li>{@code student.total_points} —— 历史累计积分，<b>只增不减</b>，是光荣榜排名与等级评定的唯一依据；</li>
 *   <li>{@code student.current_points} —— 当前可用积分，用于商城兑换与惩罚扣减。</li>
 * </ul>
 * 各业务路径对两个字段的影响是不同的：
 * <table border="1">
 *   <caption>积分变动对照表</caption>
 *   <tr><th>业务</th><th>type</th><th>可用积分</th><th>累计积分</th><th>用途</th></tr>
 *   <tr><td>活动打卡奖励</td><td>1</td><td>+</td><td>+</td><td>可花、可排名</td></tr>
 *   <tr><td>商城兑换扣减</td><td>2</td><td>-</td><td>不动</td><td>只花，不影响历史荣誉</td></tr>
 *   <tr><td>人工调账</td><td>3</td><td>±</td><td>仅正向 +</td><td>正向视为奖励</td></tr>
 *   <tr><td>订单取消返还</td><td>4</td><td>+</td><td>不动</td><td>回退支出，不算新增收入</td></tr>
 * </table>
 * 一句话：<b>累计只由「奖励类」事件驱动，可用才是真正能花掉的余额。</b>
 *
 * <h3>二、流水唯一出口</h3>
 * 任何一次积分变动，最终都必须经 {@link PointLedger#record} 落一条可对账流水，
 * 以保证「学生余额之和 == 流水净额」。禁止绕过本类直接改 {@code student} 表的积分字段，
 * 否则会破坏对账性，管理端流水大盘（{@link #getLedgerMetrics()}）也会随之失真。
 *
 * <h3>三、并发安全策略</h3>
 * 所有加减分一律走 {@code StudentMapper} 上的原子 SQL（{@code UPDATE ... SET x = x ± ?}），
 * 而不是「先查再算再写」。扣减额外携带 {@code WHERE current_points >= #{points}} 条件，
 * 由数据库保证余额不会被扣成负数。
 *
 * @see com.campus.modules.mall.service.PointLedger
 * @see com.campus.modules.mall.entity.PointRecordType
 */
import com.campus.modules.student.strategy.PointCalculationResult;
import com.campus.modules.student.strategy.PointContext;
import com.campus.modules.student.strategy.PointStrategyContext;
import lombok.RequiredArgsConstructor;

/**
 * 积分与学分账户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentScoreServiceImpl implements StudentScoreService {

    private final StudentMapper studentMapper;
    private final StudentPointRecordMapper pointRecordMapper;
    private final PointLedger pointLedger;
    private final SysNoticeService sysNoticeService;
    private final PointStrategyContext pointStrategyContext;

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

    /**
     * 查询光荣榜榜单 + 本人实时位次 + 距上一名分差。
     *
     * <p><b>排序口径固定为 {@code total_points DESC, student_no ASC}</b>：
     * 按累计积分降序排名，同分时用学号升序作为稳定兜底键。这个兜底键不能省——
     * 否则同分学生的相对顺序将由数据库自行决定，翻页时会出现「同一个人在不同页重复出现或消失」的抖动。
     * 分页列表序号与全校精确名次必须基于同一排序口径，改动排序时两边要同步改。
     *
     * <p>本人位次不使用分页列表推算（本人可能不在当前页），而是通过
     * {@code COUNT(*) + 1} 实时算出全校精确排名；距上一名的分差用偏移分页取「第 exactRank-1 条」得到，
     * 并与总分差做 {@code Math.max(0, ...)} 兜底，防止并发写入瞬间出现负分差。
     *
     * @param page             页码，从 1 开始
     * @param size             每页条数
     * @param college          按学院过滤榜单，为空表示查全校
     * @param currentStudentId 当前登录学生 ID，为空则不返回本人位次与分差
     */
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

    /**
     * 管理员人工调账：正向加分（奖励）或负向扣分（惩罚）。
     *
     * <p>扣分采用<b>双重防负</b>，两道防线缺一不可：
     * <ol>
     *   <li>Java 侧预校验 —— 目的是给管理员返回一句可读的失败原因（「余额仅剩 N 分」），
     *       避免明知会失败还要打一次数据库；</li>
     *   <li>SQL 侧条件更新 —— {@code deductCurrentPoints} 携带 {@code WHERE current_points >= #{points}}，
     *       这是真正挡住并发的防线。只有第 1 道，两个请求同时读到「余额 10、各扣 10」就会双双通过，
     *       最终扣成负数。</li>
     * </ol>
     *
     * <p>加分则同时增加可用积分与累计积分（视为一次荣誉奖励）；扣分只动可用积分，
     * 不动累计积分，即<b>积分可以花掉，但历史荣誉不被撤销</b>。
     *
     * <p>调账成功后向目标学生推送站内信 + WebSocket 实时提醒，但该通知属旁路逻辑，
     * 失败只记 WARN 不影响调账结果（调账已落库，不应因通知失败而回滚）。
     *
     * @param dto     调账参数（目标学生、变动分值、调账原因、可选审批单号）
     * @param adminId 操作管理员 ID，写入流水的 operator_id 用于责任追溯
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustPoints(PointAdjustDTO dto, Long adminId) {
        Student student = studentMapper.selectById(dto.getStudentId());
        if (student == null || (student.getDeleted() != null && student.getDeleted() == 1)) {
            throw new BusinessException("目标学生学籍档案不存在");
        }

        int beforeCurrent = student.getCurrentPoints() != null ? student.getCurrentPoints() : 0;
        PointContext context = new PointContext(student.getId(), beforeCurrent, dto.getPoints(), dto.getApprovalNo(), null, adminId, dto.getReason());
        PointCalculationResult calc = pointStrategyContext.execute(3, context);

        int change = calc.pointChange();
        if (change < 0) {
            int deduct = Math.abs(change);
            int rows = studentMapper.deductCurrentPoints(student.getId(), deduct);
            if (rows == 0) {
                throw new BusinessException("扣减可用积分失败，并发冲突或余额不足");
            }
        } else if (change > 0) {
            studentMapper.addCurrentPoints(student.getId(), change);
            if (calc.addTotalPoints()) {
                studentMapper.addTotalPoints(student.getId(), change);
            }
        }

        // 原子 SQL 变更后从数据库回读真实余额，确保记账流水与账户余额平账
        Integer currentDb = studentMapper.selectCurrentPoints(student.getId());
        int afterCurrent = currentDb != null ? currentDb : calc.afterPoints();
        String remark = calc.remark();
        if (StringUtils.hasText(dto.getApprovalNo())) {
            remark = String.format("调账审批单【%s】: %s", dto.getApprovalNo().trim(), remark);
        }
        pointLedger.record(student.getId(), 3, change, afterCurrent, remark, calc.bizNo(), null, adminId);

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

        String actTitle = StringUtils.hasText(activityTitle) ? activityTitle.trim() : "校园活动";
        String defaultRemark = String.format("参与活动打卡奖励【%s】", actTitle);
        PointContext context = new PointContext(studentId, beforeCurrent, pointsAwarded, "ACT-CHK-" + studentActivityId, studentActivityId, 0L, defaultRemark);
        PointCalculationResult calc = pointStrategyContext.execute(1, context);

        // 1. 原子增加可用积分与历史累计积分
        studentMapper.addCurrentPoints(studentId, calc.pointChange());
        if (calc.addTotalPoints()) {
            studentMapper.addTotalPoints(studentId, calc.pointChange());
        }

        // 原子 SQL 变更后从数据库回读真实余额，确保记账流水与账户余额平账
        Integer currentDb = studentMapper.selectCurrentPoints(studentId);
        int afterCurrent = currentDb != null ? currentDb : calc.afterPoints();

        // 2. 写入财务级对账流水 (通过 PointLedger 统一落账)
        pointLedger.record(studentId, 1, calc.pointChange(), afterCurrent,
                calc.remark(), calc.bizNo(), studentActivityId, 0L);

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

    /**
     * 流水实体分页 -> 展示 VO 分页。
     *
     * <p>除字段搬运外，这里还负责生成前端要展示的三类「可读标识」，规则如下
     * （这些编码<b>只用于展示与人工核对，不是数据库主键</b>，不要拿去反查业务表）：
     * <ul>
     *   <li>台账单号 recordNo = {@code PT-yyyyMMdd-流水ID(4位)}，例 {@code PT-20260921-0107}；
     *       日期取自 create_time，取不到时兜底默认日期避免格式化 NPE。</li>
     *   <li>业务单号 businessId：打卡 {@code ACT-参与记录ID}、兑换与退单 {@code ORD-订单ID}、
     *       其他有关联 {@code SYS-关联ID}、无关联 {@code OA-流水ID}。</li>
     *   <li>操作人 operatorName：operator_id &gt; 0 视为管理员操作，否则视为系统自动触发
     *       （定时关单、MQ 延时消息等场景写入的是 0L）。</li>
     * </ul>
     *
     * <p>学生信息通过一次 {@code selectBatchIds} 批量捞取后内存拼装，避免逐条流水查学生表造成 N+1。
     */
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
            String typeDesc = PointRecordType.of(r.getType()).getDescription();

            // 按业务归属给流水打标签供前端展示：打卡归活动域；兑换(2)与退单返还(4)都是礼品域的事；
            // 其余（人工调账等）统一视作系统操作。注意 4 归 GIFT 而非独立类型，因为它本质是兑换的逆向流水。
            String businessType = switch (r.getType() != null ? r.getType() : 0) {
                case 1 -> "ACTIVITY";
                case 2, 4 -> "GIFT";
                default -> "SYSTEM";
            };

            // create_time 由库默认值填充，理论上不为空；此处兜底是为了防止历史脏数据触发 NPE
            String dateStr = r.getCreateTime() != null ? r.getCreateTime().format(dtf) : "20260908";
            String recordNo = "PT-" + dateStr + "-" + String.format("%04d", r.getId());

            // 业务单号前缀取决于流水来源：打卡挂活动参与记录，兑换/退单挂礼品订单
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

            // operator_id 为 0/null 表示由系统自动触发（超时关单、MQ 消费等无人工介入的路径）
            String operatorName = r.getOperatorId() != null && r.getOperatorId() > 0 ? "管理员#" + r.getOperatorId() : "系统自动触发";

            String resolvedBizNo = StringUtils.hasText(r.getBizNo()) ? r.getBizNo() : businessId;

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
                    .bizNo(resolvedBizNo)
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
