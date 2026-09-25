package com.campus.modules.activity.chain;

import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.entity.Activity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 责任链节点 5：地理围栏与 GPS 距离核验
 */
@Slf4j
@Component
public class GeoFenceCheckinHandler extends AbstractCheckinHandler {

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    protected void doHandle(CheckinContext context) {
        Activity a = context.getActivity();
        ActivityCheckinDTO dto = context.getDto();

        if (a != null && Objects.equals(a.getLocationCheckEnabled(), 1)) {
            if (dto.getCheckinLatitude() == null || dto.getCheckinLongitude() == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请提供定位信息");
            }
            if (!isValidCoordinate(a.getLatitude(), a.getLongitude())
                    || !isValidCoordinate(dto.getCheckinLatitude(), dto.getCheckinLongitude())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "定位信息无效");
            }
            int radius = a.getLocationRadius() == null ? 300 : a.getLocationRadius();
            if (radius <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "活动签到范围配置无效");
            }
            int dist = distance(a.getLatitude(), a.getLongitude(), dto.getCheckinLatitude(), dto.getCheckinLongitude());
            if (dist > radius) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "超出活动签到范围");
            }
            context.setCheckinDistance(dist);
            if (context.getStudentActivity() != null) {
                context.getStudentActivity().setCheckinDistance(dist);
            }
        }
    }

    private boolean isValidCoordinate(java.math.BigDecimal lat, java.math.BigDecimal lon) {
        return lat != null && lon != null
                && lat.compareTo(java.math.BigDecimal.valueOf(-90)) >= 0
                && lat.compareTo(java.math.BigDecimal.valueOf(90)) <= 0
                && lon.compareTo(java.math.BigDecimal.valueOf(-180)) >= 0
                && lon.compareTo(java.math.BigDecimal.valueOf(180)) <= 0;
    }

    private int distance(java.math.BigDecimal lat1, java.math.BigDecimal lon1, java.math.BigDecimal lat2, java.math.BigDecimal lon2) {
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
