package com.mall.api.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 密码校验请求（内部契约 DTO：登录时 auth 转调 member 核对 BCrypt）
 * @author renmingl
 * @date 2026-08-26 09:33:18
 */
@Data
public class VerifyPasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 明文密码（仅服务间调用传递，不落日志） */
    @NotBlank(message = "密码不能为空")
    private String rawPassword;
}
