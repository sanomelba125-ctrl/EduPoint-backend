package com.campus.modules.activity.chain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.enums.ActivityCheckinCodeStatus;
import com.campus.modules.activity.mapper.ActivityCheckinCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 责任链节点 4：动态签到码有效性核验。
 *
 * <p><b>校验口径：以服务端数据库记录为唯一权威。</b>
 * 签到码由管理端调用 {@code ActivityService#generateQrCode} 生成时落库，
 * 这里按 (activityId, nonce, sign) 精确匹配一条 status=0 且未过期的记录，
 * 匹配不到即拒绝。由于攻击者无法伪造数据库记录，因此该查询本身就是完整的防伪凭证核验。
 * 命中记录后调用 {@code markUsed} 记录审计轨迹。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrCodeHmacCheckinHandler extends AbstractCheckinHandler {

    private final ActivityCheckinCodeMapper codeMapper;

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    protected void doHandle(CheckinContext context) {
        ActivityCheckinDTO dto = context.getDto();
        Long activityId = dto.getActivityId();

        if (dto.getNonce() == null || dto.getSign() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "签到码无效或已过期");
        }

        // 验证动态签到码在 DB 中是否存在且有效（status=0，未过期）
        ActivityCheckinCode code = codeMapper.selectOne(new LambdaQueryWrapper<ActivityCheckinCode>()
                .eq(ActivityCheckinCode::getActivityId, activityId)
                .eq(ActivityCheckinCode::getNonce, dto.getNonce())
                .eq(ActivityCheckinCode::getSign, dto.getSign())
                .eq(ActivityCheckinCode::getStatus, ActivityCheckinCodeStatus.UNUSED.getCode()));

        if (code == null || code.getExpireTime() == null || code.getExpireTime().isBefore(LocalDateTime.now())) {
            log.debug("签到码核验失败: activityId={}, nonce={}", activityId, dto.getNonce());
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "签到码无效或已过期");
        }

        context.setCheckinCode(code);
    }
}
