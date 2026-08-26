package com.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 买家登录请求
 * @author renmingl
 * @date 2026-08-26 18:31:19
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码 uuid（登录连续失败可启用验证码；本实现固定要求） */
    @NotBlank(message = "验证码不能为空")
    private String captchaUuid;

    /** 图形验证码内容 */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
