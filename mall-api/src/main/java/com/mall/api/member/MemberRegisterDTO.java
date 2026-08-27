package com.mall.api.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 买家注册请求（内部契约 DTO）
 * @author renmingl
 * @date 2026-08-26 21:31:29
 */
@Data
public class MemberRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录账号（4~32 位） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在 4~32 位之间")
    private String username;

    /** 密码（6~32 位，BCrypt 加密存储） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    /** 昵称 */
    private String nickname;

    /** 手机号（无手机号传 null，勿传空串；最长 20 位与表列一致） */
    @Size(max = 20, message = "手机号长度不能超过 20 位")
    private String phone;

    /** 邮箱 */
    private String email;
}
