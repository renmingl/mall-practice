package com.mall.auth.controller;

import com.mall.auth.dto.ForgotPasswordRequest;
import com.mall.auth.dto.LoginRequest;
import com.mall.auth.dto.LoginResponse;
import com.mall.auth.dto.RefreshRequest;
import com.mall.auth.dto.RegisterRequest;
import com.mall.auth.service.AuthService;
import com.mall.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家认证接口（经网关白名单放行）：注册 / 登录 / 刷新 / 退出 / 找回密码
 * @author renmingl
 * @date 2026-08-26 09:03:02
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    /** 注册（图形验证码 + 注册即登录） */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /** 登录（图形验证码） */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 刷新令牌 */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    /** 退出登录（access 进黑名单） */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = HEADER_AUTHORIZATION, required = false) String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            authService.logout(authorization.substring(BEARER_PREFIX.length()));
        }
        return Result.success();
    }

    /** 找回密码（短信验证码） */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return Result.success();
    }
}
