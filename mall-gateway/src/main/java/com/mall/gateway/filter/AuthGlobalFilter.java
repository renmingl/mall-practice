package com.mall.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.gateway.config.WebClientConfig;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 鉴权全局过滤器（1.3 网关鉴权：token 有效性校验集中到网关，业务服务信任透传头）
 * 流程：白名单放行 → 校验 Authorization: Bearer → WebClient 调 auth /internal/auth/check
 * （auth 查 Redis 黑名单，网关无 Redis 依赖）→ 通过后透传 X-User-Id / X-User-Type / X-User-Perms
 * 覆盖客户端伪造的同名头 → 失败返回 401 JSON
 * @author renmingl
 * @date 2026-08-26 12:19:35
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 透传下游的请求头（下游服务据此识别当前用户/权限） */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    public static final String HEADER_USER_PERMS = "X-User-Perms";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_CHECK_URL = "lb://mall-auth/internal/auth/check";

    /** 白名单：登录注册/验证码/刷新/退出/找回密码/后台登录/骨架验证/接口文档/健康检查 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/captcha", "/api/auth/admin/login", "/api/common/",
            "/doc.html", "/webjars/", "/v3/api-docs", "/actuator/"
    };

    private final WebClientConfig webClientConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthGlobalFilter(WebClientConfig webClientConfig) {
        this.webClientConfig = webClientConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange.getResponse(), "未登录或登录已过期");
        }
        return webClientConfig.webClientBuilder().build().post()
                .uri(AUTH_CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"" + token + "\"}")
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    Object code = body.get("code");
                    if (code instanceof Number number && number.intValue() == 200) {
                        return chain.filter(passUserContext(exchange, (Map<?, ?>) body.get("data")));
                    }
                    return unauthorized(exchange.getResponse(), String.valueOf(body.get("message")));
                })
                .onErrorResume(e -> unauthorized(exchange.getResponse(), "鉴权服务不可用，请稍后重试"));
    }

    /** 白名单匹配（前缀匹配） */
    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /** 校验通过：写入用户上下文头（覆盖客户端伪造值）后继续转发 */
    private ServerWebExchange passUserContext(ServerWebExchange exchange, Map<?, ?> user) {
        if (user == null) {
            return exchange;
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, String.valueOf(user.get("userId")))
                .header(HEADER_USER_TYPE, String.valueOf(user.get("userType")))
                .header(HEADER_USER_PERMS, user.get("perms") == null ? "" : String.valueOf(user.get("perms")))
                .build();
        return exchange.mutate().request(request).build();
    }

    /** 401 响应（与业务侧 Result 结构一致） */
    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("message", StringUtils.hasText(message) ? message : "未登录或登录已过期");
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        // 在 TraceIdGlobalFilter（HIGHEST_PRECEDENCE）之后执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
