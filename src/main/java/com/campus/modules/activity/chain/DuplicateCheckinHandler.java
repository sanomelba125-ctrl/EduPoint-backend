package com.campus.modules.activity.chain;

import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.entity.StudentActivity;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 责任链节点 3：防重复打卡核验
 * 大屏幕广播码模式下，允许多名学生扫同一个动态码，但单个学生只能打卡一次（status=2 拒绝）
 */
@Component
public class DuplicateCheckinHandler extends AbstractCheckinHandler {

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    protected void doHandle(CheckinContext context) {
        StudentActivity rec = context.getStudentActivity();
        if (rec != null && Objects.equals(rec.getStatus(), 2)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "您已完成签到，请勿重复打卡");
        }
    }
}
