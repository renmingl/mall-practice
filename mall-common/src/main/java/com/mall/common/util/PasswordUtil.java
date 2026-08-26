package com.mall.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具：BCrypt 加密（加盐慢哈希，不用 MD5）
 * 买家密码校验在 mall-member、后台 admin 密码校验在 mall-auth，两侧统一走本工具。
 * @author renmingl
 * @date 2026-08-26 16:24:55
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /** 明文密码加密（每次生成不同盐值，可直接比较 matches） */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 明文密码与 BCrypt 密文比对 */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
