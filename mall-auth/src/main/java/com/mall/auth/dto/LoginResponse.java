package com.mall.auth.dto;

import lombok.Data;

import java.util.Map;

/**
 * 登录/注册成功响应：access + refresh 双令牌 + 用户信息
 * @author renmingl
 * @date 2026-08-26 09:15:42
 */
@Data
public class LoginResponse {

    /** 访问令牌（Authorization: Bearer xxx） */
    private String accessToken;

    /** 刷新令牌（过期后换取新 access） */
    private String refreshToken;

    /** 令牌类型 */
    private String tokenType = "Bearer";

    /** 有效期（秒） */
    private long expiresIn;

    /** 用户信息：id/username/nickname/avatar/userType/perms/level/points */
    private Map<String, Object> user;
}
