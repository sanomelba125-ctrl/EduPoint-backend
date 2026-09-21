package com.campus.modules.mall.service.impl;

import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 积分流水账本统一实现类。
 *
 * <p>本实现被刻意做得极薄，只承担「把一笔已经发生的积分变动写成可对账流水」这一件事。
 * 它是全平台唯一的流水写入口，任何在此处追加的业务判断都会让账变得不可推理，因此有三条明确边界：
 * <ul>
 *   <li><b>不改余额</b> —— 余额由调用方通过 StudentMapper 的原子 SQL 变更，本类只负责记账，
 *       避免「改余额」与「记流水」出现两套口径；</li>
 *   <li><b>不做幂等判断</b> —— 幂等由业务侧负责（打卡记录唯一约束、订单状态守卫条件更新等），
 *       账本层再判断一次会与业务状态机重复，且很难始终保持一致；</li>
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
        pointRecordMapper.insert(record);
        log.info("积分账本入账成功: studentId={}, type={}, change={}, before={}, after={}, refId={}",
                studentId, type, change, record.getBeforePoints(), afterPoints, refId);
    }
}
