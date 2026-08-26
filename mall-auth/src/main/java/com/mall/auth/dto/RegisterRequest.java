package com.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家注册请求（图形验证码防机器，12.5）
 * @author renmingl
 * @date 2026-08-26 16:28:00
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在 4~32 位之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    private String nickname;

    private String phone;

    private String email;

    /** 图形验证码 uuid（先调 /api/auth/captcha 获取） */
    @NotBlank(message = "验证码不能为空")
    private String captchaUuid;

    /** 图形验证码内容 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
