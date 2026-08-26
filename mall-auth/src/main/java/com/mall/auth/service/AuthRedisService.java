package com.mall.auth.service;

import com.mall.auth.config.JwtProperties;
import com.mall.common.exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 认证 Redis 存储：图形验证码 / 短信验证码（模拟）/ JWT 黑名单 / 刷新令牌 / 用户令牌跟踪
 * 网关不依赖 Redis（1.2 设计：token 有效性由 auth 校验，网关经 WebClient 透传结果）
 * @author renmingl
 * @date 2026-08-26 14:36:46
 */
@Service
@RequiredArgsConstructor
public class AuthRedisService {

    public static final String KEY_CAPTCHA = "auth:captcha:";
    public static final String KEY_SMS = "auth:sms:";
    /** 短信发送频控键（同一手机号 60 秒内仅允许发送一次） */
    public static final String KEY_SMS_LIMIT = "auth:sms:limit:";
    public static final String KEY_BLACKLIST = "auth:blacklist:";
    public static final String KEY_REFRESH = "auth:refresh:";
    /** 用户维度令牌集合（踢下线：退出登录/禁用账号/重置密码/角色变更时一次性失效） */
    public static final String KEY_USER_TOKENS = "auth:user:tokens:";

    /** 图形验证码有效期 */
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    /** 短信验证码有效期 */
    private static final Duration SMS_TTL = Duration.ofMinutes(5);
    /** 短信发送最小间隔（防短信轰炸） */
    private static final Duration SMS_INTERVAL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    // ---------- 图形验证码 ----------

    public void saveCaptcha(String uuid, String code) {
        redisTemplate.opsForValue().set(KEY_CAPTCHA + uuid, code.toLowerCase(), CAPTCHA_TTL);
    }

    /** 校验并删除（一次性：校验成功即作废，防重放） */
    public void verifyCaptcha(String uuid, String code) {
        if (!StringUtils.hasText(uuid) || !StringUtils.hasText(code)) {
            throw new BizException("验证码不能为空");
        }
        String saved = redisTemplate.opsForValue().get(KEY_CAPTCHA + uuid);
        if (!StringUtils.hasText(saved)) {
            throw new BizException("验证码已过期，请重新获取");
        }
        redisTemplate.delete(KEY_CAPTCHA + uuid);
        if (!saved.equals(code.toLowerCase())) {
            throw new BizException("验证码错误");
        }
    }

    // ---------- 短信验证码（模拟发送：真实短信网关后续接入，开发期直接返回验证码） ----------

    /**
     * 发送（模拟）短信验证码：同一手机号 60 秒内仅允许一次（Redis SETNX 频控），防短信轰炸
     */
    public String saveSmsCode(String phone) {
        Boolean first = redisTemplate.opsForValue().setIfAbsent(KEY_SMS_LIMIT + phone, "1", SMS_INTERVAL);
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException("验证码发送过于频繁，请 60 秒后再试");
        }
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        redisTemplate.opsForValue().set(KEY_SMS + phone, code, SMS_TTL);
        return code;
    }

    /** 校验并删除（找回密码用） */
    public void verifySmsCode(String phone, String code) {
        String saved = redisTemplate.opsForValue().get(KEY_SMS + phone);
        if (!StringUtils.hasText(saved) || !saved.equals(code)) {
            throw new BizException("短信验证码错误或已过期");
        }
        redisTemplate.delete(KEY_SMS + phone);
    }

    // ---------- JWT 黑名单（退出登录后主动失效） ----------

    /** 加入黑名单，TTL 对齐令牌剩余有效期 */
    public void addToBlacklist(Claims claims) {
        long remainSeconds = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        if (remainSeconds > 0) {
            redisTemplate.opsForValue().set(KEY_BLACKLIST + claims.getId(), "1", remainSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_BLACKLIST + jti));
    }

    // ---------- 刷新令牌 ----------

    /** 刷新令牌持久化：TTL 与 JwtProperties.refreshExpireDays 保持一致（改配置自动同步） */
    public void saveRefreshToken(String jti, String userType) {
        redisTemplate.opsForValue().set(KEY_REFRESH + jti, userType,
                Duration.ofDays(jwtProperties.getRefreshExpireDays()));
    }

    public boolean isValidRefreshToken(String jti, String userType) {
        String saved = redisTemplate.opsForValue().get(KEY_REFRESH + jti);
        return userType.equals(saved);
    }

    public void deleteRefreshToken(String jti) {
        redisTemplate.delete(KEY_REFRESH + jti);
    }

    // ---------- 用户令牌跟踪（踢下线支持） ----------

    /** 登录/刷新时登记：该用户当前有效的 access/refresh jti 集合（TTL 对齐 refresh 有效期） */
    public void trackTokens(String userType, Long userId, String accessJti, String refreshJti) {
        String key = KEY_USER_TOKENS + userType + ":" + userId;
        redisTemplate.opsForSet().add(key, accessJti, refreshJti);
        redisTemplate.expire(key, Duration.ofDays(jwtProperties.getRefreshExpireDays()));
    }

    /** 刷新轮换时从集合移除旧 refresh jti（已被黑名单/删除，避免集合无限膨胀） */
    public void untrackToken(String userType, Long userId, String jti) {
        redisTemplate.opsForSet().remove(KEY_USER_TOKENS + userType + ":" + userId, jti);
    }

    /**
     * 用户全部令牌一次性失效（退出登录 / 禁用账号 / 重置密码 / 角色权限变更时调用）：
     * 集合内全部 jti 进黑名单（TTL 对齐 access 最长有效期，refresh 删除后自然失效）+ 删除 refresh 键 + 清空集合
     */
    public void invalidateUserTokens(String userType, Long userId) {
        String key = KEY_USER_TOKENS + userType + ":" + userId;
        Set<String> jtis = redisTemplate.opsForSet().members(key);
        if (jtis != null && !jtis.isEmpty()) {
            long ttlSeconds = jwtProperties.getAccessExpireMinutes() * 60;
            for (String jti : jtis) {
                redisTemplate.opsForValue().set(KEY_BLACKLIST + jti, "1", ttlSeconds, TimeUnit.SECONDS);
                redisTemplate.delete(KEY_REFRESH + jti);
            }
            redisTemplate.delete(key);
        }
    }
}
