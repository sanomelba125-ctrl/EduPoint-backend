package com.campus.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类
 * 基于 Google ZXing 实现，支持生成 Base64 Data URL 格式图片
 */
public final class QrCodeUtil {

    private QrCodeUtil() {
    }

    /**
     * 生成标准二维码 Base64 Data URL（如 data:image/png;base64,...）
     *
     * @param content 二维码承载内容（URL、加密Token等）
     * @param width   宽度（像素）
     * @param height  高度（像素）
     * @return Base64 Data URL
     */
    public static String generateQrCodeBase64(String content, int width, int height) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("二维码内容不能为空");
        }
        int w = width > 0 ? width : 300;
        int h = height > 0 ? height : 300;

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, w, h, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] bytes = outputStream.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("生成二维码失败: " + e.getMessage(), e);
        }
    }
}
