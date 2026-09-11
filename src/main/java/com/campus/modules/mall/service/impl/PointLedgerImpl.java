package com.campus.modules.mall.service.impl;

import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 积分流水账本统一实现类
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
