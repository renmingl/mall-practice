package com.mall.auth.dto;

import lombok.Data;

/**
 * 网关令牌校验结果（内部契约）：校验通过时返回用户上下文，网关透传下游服务
 * @author renmingl
 * @date 2026-08-26 17:45:06
 */
@Data
public class TokenCheckResponse {

    /** 用户 ID（member.id 或 admin_user.id） */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户类型：MEMBER 买家 / ADMIN 后台管理员 */
    private String userType;

    /** 后台权限标识（逗号分隔，买家为 null）；网关透传 X-User-Perms 供下游 @PreAuthorize */
    private String perms;

    /** 令牌过期时间戳（毫秒） */
    private long expireAt;
}
