package com.mall.auth.util;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 图形验证码工具（12.5 登录注册防机器）：4 位数字+字母，干扰线/噪点，输出 base64 PNG
 * 自绘实现（避免引入 kaptcha 等第三方与 Boot 4 的兼容成本）
 * @author renmingl
 * @date 2026-08-26 10:41:05
 */
public final class CaptchaUtil {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final char[] CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaUtil() {
    }

    /** 生成验证码：code 为明文（Redis 存小写），imgBase64 为 data:image/png;base64 */
    public static Captcha generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return new Captcha(code.toString(), toBase64(draw(code.toString())));
    }

    private static BufferedImage draw(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 背景
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        // 干扰线（3 条）
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }
        // 字符（随机颜色/旋转）
        Font font = new Font("Arial", Font.BOLD, 28);
        g.setFont(font);
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(30 + RANDOM.nextInt(140), 30 + RANDOM.nextInt(140), 30 + RANDOM.nextInt(140)));
            double angle = (RANDOM.nextDouble() - 0.5) * 0.6;
            g.rotate(angle, 20 + i * 26, HEIGHT / 2.0);
            g.drawString(String.valueOf(code.charAt(i)), 18 + i * 26, 28);
            g.rotate(-angle, 20 + i * 26, HEIGHT / 2.0);
        }
        // 噪点（60 个）
        for (int i = 0; i < 60; i++) {
            g.setColor(new Color(RANDOM.nextInt(255), RANDOM.nextInt(255), RANDOM.nextInt(255)));
            g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
        }
        g.dispose();
        return image;
    }

    private static String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("验证码图片生成失败", e);
        }
    }

    /** 验证码结果：code（明文，仅服务端比对用）+ imgBase64（下发前端展示） */
    public record Captcha(String code, String imgBase64) {
    }
}
