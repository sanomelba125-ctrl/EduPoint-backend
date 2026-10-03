package com.campus.modules.certificate.service.impl;

import com.campus.config.QiniuProperties;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.certificate.entity.Certificate;
import com.campus.modules.certificate.mapper.CertificateMapper;
import com.campus.modules.certificate.service.CertificateRenderService;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qiniu.http.Response;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * P2-12: 电子荣誉证书动态渲染与云存储上传落地引擎
 * 采用 Java Graphics2D 像素级合成：高清羊皮纸双金边框底纹、学生姓名、学号、活动名称、公章与 ZXing 防伪验真二维码
 */
@Slf4j
@Service
public class CertificateRenderServiceImpl implements CertificateRenderService {

    private final CertificateMapper certificateMapper;
    private final StudentMapper studentMapper;
    private final ActivityMapper activityMapper;
    private final QiniuProperties qiniuProperties;
    private final UploadManager uploadManager;
    private final Auth qiniuAuth;

    private static final int CANVAS_WIDTH = 1200;
    private static final int CANVAS_HEIGHT = 840;

    @Autowired
    public CertificateRenderServiceImpl(CertificateMapper certificateMapper,
                                        StudentMapper studentMapper,
                                        ActivityMapper activityMapper,
                                        QiniuProperties qiniuProperties,
                                        UploadManager uploadManager,
                                        Auth qiniuAuth) {
        this.certificateMapper = certificateMapper;
        this.studentMapper = studentMapper;
        this.activityMapper = activityMapper;
        this.qiniuProperties = qiniuProperties;
        this.uploadManager = uploadManager;
        this.qiniuAuth = qiniuAuth;
    }

    public CertificateRenderServiceImpl(CertificateMapper certificateMapper,
                                        StudentMapper studentMapper,
                                        ActivityMapper activityMapper,
                                        QiniuProperties qiniuProperties) {
        this(certificateMapper, studentMapper, activityMapper, qiniuProperties, null, null);
    }

    @Override
    public String renderAndUpload(Long certificateId) {
        if (certificateId == null) {
            return null;
        }
        Certificate cert = certificateMapper.selectById(certificateId);
        if (cert == null) {
            log.warn("证书不存在，跳过渲染: id={}", certificateId);
            return null;
        }

        Student student = studentMapper.selectById(cert.getStudentId());
        Activity activity = activityMapper.selectById(cert.getActivityId());

        byte[] pngBytes = drawCertificatePng(cert, student, activity);
        if (pngBytes == null || pngBytes.length == 0) {
            log.error("证书图片二进制生成为空: id={}", certificateId);
            return null;
        }

        String fileUrl = null;

        // 1. 尝试上传至七牛云存储 (仅在开启七牛云且配置有效时)
        if (qiniuProperties.isQiniuEnabled() && uploadManager != null && qiniuAuth != null) {
            try {
                String key = "certificates/" + cert.getCertificateNo() + ".png";
                String uploadToken = qiniuAuth.uploadToken(qiniuProperties.getBucket());
                Response response = uploadManager.put(pngBytes, key, uploadToken);
                if (response.isOK()) {
                    String domain = qiniuProperties.getDomainUrl();
                    if (domain != null && !domain.isBlank()) {
                        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                            domain = "http://" + domain;
                        }
                        if (!domain.endsWith("/")) {
                            domain += "/";
                        }
                        fileUrl = domain + key;
                    } else {
                        fileUrl = key;
                    }
                    log.info("证书已成功上传至七牛云: certificateNo={}, url={}", cert.getCertificateNo(), fileUrl);
                }
            } catch (Exception e) {
                log.warn("上传七牛云异常，自动降级为本地持久化: certificateNo={}, error={}", cert.getCertificateNo(), e.getMessage());
            }
        }

        // 2. 本地静态存储兜底（开发环境或云存储不可用时）
        if (fileUrl == null) {
            try {
                Path dir = Paths.get("uploads", "certificates");
                Files.createDirectories(dir);
                Path targetPath = dir.resolve(cert.getCertificateNo() + ".png");
                Files.write(targetPath, pngBytes);
                fileUrl = "/api/certificate/file/" + cert.getCertificateNo() + ".png";
                log.info("证书已保存至本地静态目录: path={}, url={}", targetPath.toAbsolutePath(), fileUrl);
            } catch (Exception e) {
                log.error("本地证书图片保存失败: certificateNo={}, error={}", cert.getCertificateNo(), e.getMessage());
                fileUrl = "/api/certificate/file/" + cert.getCertificateNo() + ".png";
            }
        }

        // 3. 回写 certificate.file_url 字段
        cert.setFileUrl(fileUrl);
        certificateMapper.updateById(cert);
        return fileUrl;
    }

    @Override
    public byte[] renderToPngBytesByNo(String certificateNo) {
        if (certificateNo == null || certificateNo.isBlank()) {
            return null;
        }
        // 1. 优先尝试从本地磁盘缓存读取
        Path localPath = Paths.get("uploads", "certificates", certificateNo.trim() + ".png");
        if (Files.exists(localPath)) {
            try {
                return Files.readAllBytes(localPath);
            } catch (Exception ignored) {
            }
        }

        // 2. 磁盘不存在则即时动态渲染
        Certificate cert = certificateMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertificateNo, certificateNo.trim()));
        if (cert == null) {
            return null;
        }
        Student student = studentMapper.selectById(cert.getStudentId());
        Activity activity = activityMapper.selectById(cert.getActivityId());
        byte[] pngBytes = drawCertificatePng(cert, student, activity);

        // 写入本地供后续重复访问
        if (pngBytes != null && pngBytes.length > 0) {
            try {
                Path dir = Paths.get("uploads", "certificates");
                Files.createDirectories(dir);
                Files.write(localPath, pngBytes);
            } catch (Exception ignored) {
            }
        }
        return pngBytes;
    }

    /**
     * 核心渲染器：Java Graphics2D 像素级合成证书
     */
    private byte[] drawCertificatePng(Certificate cert, Student student, Activity activity) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            // 开启顶级抗锯齿与渲染画质
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // 1. 典雅羊皮纸底色填充
            g2d.setColor(new Color(254, 252, 247));
            g2d.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

            // 2. 双层皇家金双边框
            // 外边框
            g2d.setColor(new Color(184, 134, 11)); // DarkGoldenrod
            g2d.setStroke(new BasicStroke(4.0f));
            g2d.drawRect(32, 32, CANVAS_WIDTH - 64, CANVAS_HEIGHT - 64);

            // 内边框
            g2d.setColor(new Color(218, 165, 32)); // Goldenrod
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRect(42, 42, CANVAS_WIDTH - 84, CANVAS_HEIGHT - 84);

            // 四角古典修饰菱形角花
            drawCornerAccents(g2d, 37, 37);
            drawCornerAccents(g2d, CANVAS_WIDTH - 37, 37);
            drawCornerAccents(g2d, 37, CANVAS_HEIGHT - 37);
            drawCornerAccents(g2d, CANVAS_WIDTH - 37, CANVAS_HEIGHT - 37);

            // 3. 背景隐形防伪水印 (倾斜平铺)
            drawSecurityWatermark(g2d);

            // 4. 头部英文与主标题
            g2d.setColor(new Color(100, 116, 139));
            Font engSubFont = chooseFont(Font.SANS_SERIF, Font.BOLD, 15);
            g2d.setFont(engSubFont);
            drawCenteredString(g2d, "UNIVERSITY ACTIVITY CREDENTIAL & OFFICIAL CERTIFICATION", CANVAS_WIDTH / 2, 115);

            // 主标题
            g2d.setColor(new Color(30, 41, 59));
            Font mainTitleFont = chooseFont(Font.SERIF, Font.BOLD, 46);
            g2d.setFont(mainTitleFont);
            String titleText = (cert.getTitle() != null && !cert.getTitle().isBlank()) ? cert.getTitle() : "荣誉结项证书";
            drawCenteredString(g2d, titleText, CANVAS_WIDTH / 2, 185);

            // 金色分割线与勋章装饰
            g2d.setColor(new Color(212, 175, 55));
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawLine(CANVAS_WIDTH / 2 - 180, 215, CANVAS_WIDTH / 2 + 180, 215);

            // 5. 证书正文颁发声明
            String stuName = student != null && student.getName() != null ? student.getName() : "优秀学员";
            String stuNo = student != null && student.getStudentNo() != null ? student.getStudentNo() : "2026001";
            String actTitle = activity != null && activity.getTitle() != null ? activity.getTitle() : "校园精品实践活动";
            String issuer = cert.getIssuer() != null ? cert.getIssuer() : "校学生工作部 · 团委认证处";

            g2d.setColor(new Color(51, 65, 85));
            Font bodyFont = chooseFont(Font.SERIF, Font.PLAIN, 22);
            Font boldHighlightFont = chooseFont(Font.SERIF, Font.BOLD, 24);

            int startY = 300;
            int lineHeight = 52;

            drawCenteredString(g2d, "兹证明  " + stuName + "  同学（学号：" + stuNo + "）", CANVAS_WIDTH / 2, startY, bodyFont);
            drawCenteredString(g2d, "积极参加由 " + issuer + " 组织的", CANVAS_WIDTH / 2, startY + lineHeight, bodyFont);

            // 活动名称突出加粗高亮
            g2d.setFont(boldHighlightFont);
            g2d.setColor(new Color(15, 23, 42));
            drawCenteredString(g2d, "【 " + actTitle + " 】", CANVAS_WIDTH / 2, startY + lineHeight * 2);

            g2d.setFont(bodyFont);
            g2d.setColor(new Color(51, 65, 85));
            drawCenteredString(g2d, "在活动过程中严谨笃行、表现优异，经考核准予结业，特发此证，以资鼓励！", CANVAS_WIDTH / 2, startY + lineHeight * 3);

            // 6. 左下角：ZXing 防伪验真二维码与唯一证书编号
            int qrX = 110;
            int qrY = 590;
            int qrSize = 135;
            drawQrCode(g2d, cert.getCertificateNo(), qrX, qrY, qrSize);

            // 证书防伪编码标签
            g2d.setColor(new Color(71, 85, 105));
            Font noFont = new Font(Font.MONOSPACED, Font.BOLD, 14);
            g2d.setFont(noFont);
            g2d.drawString("NO: " + cert.getCertificateNo(), qrX, qrY + qrSize + 24);

            Font verifyHintFont = chooseFont(Font.SANS_SERIF, Font.PLAIN, 12);
            g2d.setFont(verifyHintFont);
            g2d.setColor(new Color(148, 163, 184));
            g2d.drawString("扫码验真 (Scan to Verify)", qrX, qrY + qrSize + 42);

            // 7. 右下角：签发机构、发证日期与矢量公章
            int rightAlignX = 1070;
            Font issuerFont = chooseFont(Font.SERIF, Font.BOLD, 22);
            g2d.setFont(issuerFont);
            g2d.setColor(new Color(30, 41, 59));
            drawRightAlignedString(g2d, issuer, rightAlignX, 640);

            String dateStr = cert.getIssueTime() != null
                    ? cert.getIssueTime().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                    : DateTimeFormatter.ofPattern("yyyy年MM月dd日").format(java.time.LocalDate.now());

            Font dateFont = chooseFont(Font.SERIF, Font.PLAIN, 18);
            g2d.setFont(dateFont);
            g2d.setColor(new Color(100, 116, 139));
            drawRightAlignedString(g2d, dateStr, rightAlignX, 680);

            // 盖下权威官方防伪电子印章 (覆盖在签发单位与日期上方)
            drawOfficialSeal(g2d, 905, 575);

            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("证书动态渲染生成异常: certificateId={}, error={}", cert.getId(), e.getMessage(), e);
            return null;
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 绘制古典角花
     */
    private void drawCornerAccents(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(184, 134, 11));
        g.fillRect(cx - 5, cy - 5, 10, 10);
        g.setColor(new Color(254, 252, 247));
        g.fillRect(cx - 2, cy - 2, 4, 4);
    }

    /**
     * 绘制防伪微缩水印
     */
    private void drawSecurityWatermark(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(226, 232, 240, 45)); // 极淡灰蓝
        g2.setFont(chooseFont(Font.SANS_SERIF, Font.BOLD, 52));
        g2.rotate(Math.toRadians(-25), CANVAS_WIDTH / 2.0, CANVAS_HEIGHT / 2.0);
        drawCenteredString(g2, "CAMPUS OFFICIAL CREDENTIAL", CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2 - 80);
        drawCenteredString(g2, "AUTHENTIC VERIFICATION SECURED", CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2 + 80);
        g2.dispose();
    }

    /**
     * 绘制 ZXing 二维码
     */
    private void drawQrCode(Graphics2D g, String certificateNo, int x, int y, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            String verifyContent = "https://campus.edu.cn/verify/" + certificateNo;
            BitMatrix bitMatrix = qrCodeWriter.encode(verifyContent, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // 绘制白色衬底与细边框
            g.setColor(Color.WHITE);
            g.fillRect(x - 4, y - 4, size + 8, size + 8);
            g.setColor(new Color(226, 232, 240));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(x - 4, y - 4, size + 8, size + 8);

            g.drawImage(qrImage, x, y, null);
        } catch (Exception e) {
            log.warn("生成证书二维码异常: {}", e.getMessage());
        }
    }

    /**
     * 绘制权威防伪电子印章 (印泥质感、同心双环、五角星、环形铭文)
     */
    private void drawOfficialSeal(Graphics2D g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 模拟真实按压印泥印油半透明质感，倾斜 -8 度
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
        g2.rotate(Math.toRadians(-8), x + 70, y + 70);

        Color sealRed = new Color(220, 38, 38);
        g2.setColor(sealRed);

        // 外圆环 (直径 140)
        g2.setStroke(new BasicStroke(3.5f));
        g2.drawOval(x, y, 140, 140);

        // 内圆细环 (直径 128)
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(x + 6, y + 6, 128, 128);

        // 中心五角星
        drawFivePointStar(g2, x + 70, y + 55, 18);

        // 印章顶部/中部铭文
        Font sealFont = chooseFont(Font.SERIF, Font.BOLD, 13);
        g2.setFont(sealFont);
        drawCenteredString(g2, "学工部认证", x + 70, y + 92);

        Font sealSubFont = chooseFont(Font.SANS_SERIF, Font.BOLD, 10);
        g2.setFont(sealSubFont);
        drawCenteredString(g2, "★ 防伪验讫 ★", x + 70, y + 110);

        g2.dispose();
    }

    /**
     * 绘制五角星
     */
    private void drawFivePointStar(Graphics2D g, int centerX, int centerY, int radius) {
        int[] xPoints = new int[10];
        int[] yPoints = new int[10];
        double innerRadius = radius * 0.42;

        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-18 + i * 36);
            double r = (i % 2 == 0) ? radius : innerRadius;
            xPoints[i] = (int) (centerX + r * Math.cos(angle));
            yPoints[i] = (int) (centerY - r * Math.sin(angle));
        }
        g.fillPolygon(xPoints, yPoints, 10);
    }

    private void drawCenteredString(Graphics2D g, String text, int x, int y, Font font) {
        g.setFont(font);
        drawCenteredString(g, text, x, y);
    }

    private void drawCenteredString(Graphics2D g, String text, int x, int y) {
        if (text == null) return;
        FontMetrics fm = g.getFontMetrics();
        int strWidth = fm.stringWidth(text);
        g.drawString(text, x - strWidth / 2, y);
    }

    private void drawRightAlignedString(Graphics2D g, String text, int x, int y) {
        if (text == null) return;
        FontMetrics fm = g.getFontMetrics();
        int strWidth = fm.stringWidth(text);
        g.drawString(text, x - strWidth, y);
    }

    private Font chooseFont(String preferredFamily, int style, int size) {
        // 优先使用跨平台中文字体
        String[] candidateFonts = {"Microsoft YaHei", "SimSun", "SimHei", "PingFang SC", preferredFamily};
        for (String family : candidateFonts) {
            Font f = new Font(family, style, size);
            if (f.canDisplay('中') && f.canDisplay('证')) {
                return f;
            }
        }
        return new Font(preferredFamily, style, size);
    }
}
