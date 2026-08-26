package com.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 网关令牌校验请求（内部契约：网关经 WebClient 调 auth 校验 access token）
 * @author renmingl
 * @date 2026-08-26 09:42:53
 */
@Data
public class TokenCheckRequest {

    @NotBlank(message = "令牌不能为空")
    private String token;
}
