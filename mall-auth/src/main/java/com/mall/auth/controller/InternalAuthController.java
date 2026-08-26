package com.mall.auth.controller;

import com.mall.auth.dto.TokenCheckRequest;
import com.mall.auth.dto.TokenCheckResponse;
import com.mall.auth.service.AuthRedisService;
import com.mall.auth.util.JwtUtil;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证内部接口（服务间调用，不经网关）：网关 JWT 鉴权时校验 token 有效性
 * 1.2 设计：token 校验在 auth（Redis 黑名单），网关无 Redis 依赖，经 WebClient 透传结果
 * @author renmingl
 * @date 2026-08-26 16:53:07
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final JwtUtil jwtUtil;
    private final AuthRedisService authRedisService;

    /** 令牌校验：签名/过期/黑名单任一不通过返回 401，网关据此放行或拒绝 */
    @PostMapping("/check")
    public Result<TokenCheckResponse> check(@RequestBody TokenCheckRequest request) {
        Claims claims = jwtUtil.parse(request.getToken());
        if (claims == null || authRedisService.isBlacklisted(jwtUtil.getJti(claims))) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        TokenCheckResponse response = new TokenCheckResponse();
        response.setUserId(claims.get(JwtUtil.CLAIM_USER_ID, Number.class).longValue());
        response.setUsername(claims.get(JwtUtil.CLAIM_USERNAME, String.class));
        response.setUserType(claims.get(JwtUtil.CLAIM_USER_TYPE, String.class));
        response.setPerms(claims.get(JwtUtil.CLAIM_PERMS, String.class));
        response.setExpireAt(claims.getExpiration().getTime());
        return Result.success(response);
    }
}
