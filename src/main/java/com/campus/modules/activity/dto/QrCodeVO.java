package com.campus.modules.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 活动动态签到二维码响应 VO
 * 对应接口 2.6 管理端生成/刷新活动动态签到二维码
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeVO {

    /** 活动ID */
    private Long activityId;

    /** 防伪随机 Nonce（全局唯一，不可重放） */
    private String nonce;

    /** 服务端 HMAC 签名参数（客户端打卡时原样回传用于校验） */
    private String sign;

    /** 二维码有效截止时间（格式：yyyy-MM-dd HH:mm:ss） */
    private String expireTime;

    /** 二维码有效时长（秒），前端用于倒计时展示 */
    private Integer validSeconds;

    /**
     * 供学生扫码跳转的完整 URL，包含 actId / nonce / sign 参数。
     * 学生端扫码后解析此 URL 并携带当前 GPS 坐标提交打卡请求。
     */
    private String qrContent;

    /**
     * 二维码图片 Base64 Data URL (data:image/png;base64,...)
     * 大屏端直接使用 <img :src="qrCodeBase64" /> 渲染，无需前端额外引入二维码生成库
     */
    private String qrCodeBase64;
}
