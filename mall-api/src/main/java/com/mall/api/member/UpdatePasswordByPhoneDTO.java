package com.mall.api.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 按手机号修改密码请求（内部契约 DTO：找回密码时 auth 验证码校验通过后转调 member）
 * @author renmingl
 * @date 2026-08-26 17:17:08
 */
@Data
public class UpdatePasswordByPhoneDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号（会员唯一键，用于定位账号） */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 新密码（6~32 位） */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
