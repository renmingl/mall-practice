package com.mall.auth.controller;

import com.mall.auth.dto.LoginResponse;
import com.mall.auth.service.AdminAuthService;
import com.mall.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理员认证接口（1.7 后台账号体系）：登录 / 当前用户信息
 * @author renmingl
 * @date 2026-08-26 13:21:54
 */
@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /** 后台登录（RBAC：登录后权限标识写入 JWT；图形验证码防暴力破解） */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminAuthService.login(request.getUsername(), request.getPassword(),
                request.getCaptchaUuid(), request.getCaptchaCode()));
    }

    /** 当前登录管理员信息（含角色/权限，刷新页面恢复登录态用；经网关鉴权） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader("X-User-Id") Long adminId) {
        return Result.success(adminAuthService.me(adminId));
    }

    @Data
    public static class AdminLoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        /** 图形验证码（uuid + 用户输入） */
        @NotBlank(message = "验证码不能为空")
        private String captchaUuid;
        @NotBlank(message = "验证码不能为空")
        private String captchaCode;
    }
}
