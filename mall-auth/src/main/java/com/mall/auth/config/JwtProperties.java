package com.mall.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（mall.jwt.*）：密钥 / 有效期
 * HS256 密钥长度须 ≥ 32 字节；生产环境务必改密钥并放入环境变量
 * @author renmingl
 * @date 2026-08-26 13:07:43
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.jwt")
public class JwtProperties {

    /** 签名密钥（HS256） */
    private String secret = "mall-practice-jwt-secret-key-please-change-in-prod-2026";

    /** 访问令牌有效期（分钟） */
    private long accessExpireMinutes = 30;

    /** 刷新令牌有效期（天） */
    private long refreshExpireDays = 7;
}
