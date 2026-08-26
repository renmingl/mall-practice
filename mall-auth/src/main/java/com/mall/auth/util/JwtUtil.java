package com.mall.auth.util;

import com.mall.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具：签发 / 解析（HS256）
 * claims：userId / username / userType（MEMBER|ADMIN）/ perms（后台权限标识逗号串）
 * 无状态 token 无法主动失效 → 退出登录时将 jti 写入 Redis 黑名单，校验时比对
 * @author renmingl
 * @date 2026-08-26 22:43:47
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String USER_TYPE_MEMBER = "MEMBER";
    public static final String USER_TYPE_ADMIN = "ADMIN";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_USER_TYPE = "userType";
    public static final String CLAIM_PERMS = "perms";

    private final JwtProperties properties;

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发访问令牌（含 jti，供黑名单使用） */
    public String createAccessToken(Long userId, String username, String userType, String perms) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USERNAME, username);
        claims.put(CLAIM_USER_TYPE, userType);
        if (perms != null) {
            claims.put(CLAIM_PERMS, perms);
        }
        return build(claims, properties.getAccessExpireMinutes() * 60 * 1000L);
    }

    /** 签发刷新令牌（仅 jti + 类型标识，Redis 存有效态） */
    public String createRefreshToken(Long userId, String userType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_USER_TYPE, userType);
        return build(claims, properties.getRefreshExpireDays() * 24 * 60 * 60 * 1000L);
    }

    private String build(Map<String, Object> claims, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(secretKey())
                .compact();
    }

    /** 解析令牌：签名/过期校验，非法或过期返回 null */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey()).build()
                    .parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 claims 取 jti（黑名单键用） */
    public String getJti(Claims claims) {
        return claims.getId();
    }

    /** 访问令牌有效期（秒，登录响应下发前端用） */
    public long getAccessExpireSeconds() {
        return properties.getAccessExpireMinutes() * 60;
    }
}
