package com.campus.modules.activity.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.entity.StudentActivity;
import com.campus.modules.activity.mapper.StudentActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 责任链节点 2：学生报名状态核验
 */
@Component
@RequiredArgsConstructor
public class EnrollmentCheckinHandler extends AbstractCheckinHandler {

    private final StudentActivityMapper studentActivityMapper;

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    protected void doHandle(CheckinContext context) {
        Long studentId = context.getStudentId();
        Long activityId = context.getDto().getActivityId();

        StudentActivity rec = context.getStudentActivity();
        if (rec == null) {
            rec = studentActivityMapper.selectOne(new LambdaQueryWrapper<StudentActivity>()
                    .eq(StudentActivity::getStudentId, studentId)
                    .eq(StudentActivity::getActivityId, activityId), false);
            context.setStudentActivity(rec);
        }

        if (rec == null || Objects.equals(rec.getStatus(), 0)
                || Objects.equals(rec.getStatus(), com.campus.modules.activity.enums.StudentActivityStatus.CANCELLED.getCode())) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_ENROLLED.getCode(), "请先报名活动");
        }
    }
}
