package com.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求（access 过期后用 refresh 换新）
 * @author renmingl
 * @date 2026-08-26 20:21:33
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
