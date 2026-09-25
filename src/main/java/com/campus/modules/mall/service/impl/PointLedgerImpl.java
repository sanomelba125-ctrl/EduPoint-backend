package com.campus.modules.mall.service.impl;

import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 积分流水账本统一实现类。
 *
 * <p>本实现被刻意做得极薄，只承担「把一笔已经发生的积分变动写成可对账流水」这一件事。
 * 它是全平台唯一的流水写入口，任何在此处追加的业务判断都会让账变得不可推理，因此有三条明确边界：
 * <ul>
 *   <li><b>不改余额</b> —— 余额由调用方通过 StudentMapper 的原子 SQL 变更，本类只负责记账，
 *       避免「改余额」与「记流水」出现两套口径；</li>
 *   <li><b>物理唯一键兜底幂等</b> —— 依靠 uk_biz_type (biz_no, type) 联合唯一键，
 *       并发或重试插入重复流水时抛出 DuplicateKeyException 促使外层事务回滚，杜绝重复记账；</li>
 *   <li><b>只 insert，不 update / delete</b> —— 流水是财务凭证，记错只能追加一笔冲正流水，不允许篡改历史。</li>
 * </ul>
 *
 * <p><b>核心不变量：{@code before_points + change_points == after_points} 恒成立。</b>
 * 实现上用 {@code afterPoints - change} 反推 before 值，而不是让调用方再传一次，
 * 从方法签名上就杜绝了「前后值对不上」的可能。代价是调用方<b>必须传入变更后的真实余额</b>
 * （即原子 SQL 执行完毕后回读到的值），传错会直接导致流水与余额不平。
 *
 * @see com.campus.modules.mall.entity.PointRecordType
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointLedgerImpl implements PointLedger {

    private final StudentPointRecordMapper pointRecordMapper;

    @Override
    public void record(Long studentId, int type, int change, int afterPoints,
                       String description, Long refId, Long operatorId) {
        record(studentId, type, change, afterPoints, description, null, refId, operatorId);
    }

    @Override
    public void record(Long studentId, int type, int change, int afterPoints,
                       String description, String bizNo, Long refId, Long operatorId) {
        StudentPointRecord record = new StudentPointRecord();
        record.setStudentId(studentId);
        record.setType(type);
        record.setChangePoints(change);
        // beforePoints 由 afterPoints - change 严格推导，保证 before + change == after 恒成立
        record.setBeforePoints(afterPoints - change);
        record.setAfterPoints(afterPoints);
        record.setDescription(description != null ? description.trim() : "");
        record.setRefId(refId);
        record.setOperatorId(operatorId == null ? 0L : operatorId);

        // 如果未显式传入 bizNo，则按业务类型与 refId 兜底派生，保证唯一键不为空
        String resolvedBizNo = bizNo;
        if (!StringUtils.hasText(resolvedBizNo)) {
            if (refId != null) {
                resolvedBizNo = switch (type) {
                    case 1 -> "ACT-CHK-" + refId;
                    case 2 -> "GIFT-ORD-" + refId;
                    case 4 -> "GIFT-REF-" + refId;
                    default -> "SYS-REF-" + refId;
                };
            } else {
                resolvedBizNo = "BIZ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
        }
        record.setBizNo(resolvedBizNo.trim());

        try {
            pointRecordMapper.insert(record);
            log.info("积分账本入账成功: studentId={}, type={}, change={}, before={}, after={}, bizNo={}, refId={}",
                    studentId, type, change, record.getBeforePoints(), afterPoints, record.getBizNo(), refId);
        } catch (DuplicateKeyException e) {
            log.warn("积分账本入账命中 uk_biz_type 唯一约束冲突(重复记账被拦截): studentId={}, type={}, bizNo={}, error={}",
                    studentId, type, record.getBizNo(), e.getMessage());
            throw e;
        }
    }
}
