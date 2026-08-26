package com.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 找回密码请求（短信验证码校验通过后设置新密码）
 * @author renmingl
 * @date 2026-08-26 18:49:49
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String newPassword;
}
