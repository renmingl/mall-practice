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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 鉴权全局过滤器（1.3 网关鉴权：token 有效性校验集中到网关，业务服务信任透传头）
 * 流程：白名单放行 → 校验 Authorization: Bearer → WebClient 调 auth /internal/auth/check
 * （auth 查 Redis 黑名单，网关无 Redis 依赖）→ 通过后透传 X-User-Id / X-User-Type / X-User-Perms
 * 覆盖客户端伪造的同名头；/api/admin/** 额外校验 userType=ADMIN（防买家 token 越权后台）
 * 失败返回 401（未登录）/ 403（角色不符）JSON
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

    /** 后台管理员用户类型（与 mall-auth JwtUtil.USER_TYPE_ADMIN 约定一致） */
    private static final String USER_TYPE_ADMIN = "ADMIN";

    /** 后台管理路径前缀：仅 ADMIN 角色可访问（防 MEMBER 越权） */
    private static final String ADMIN_PATH_PREFIX = "/api/admin";

    /** 白名单：登录注册/验证码/刷新/退出/找回密码/后台登录/骨架验证/接口文档/健康检查/前台商品浏览/AI 助手问答（免登录） */
    private static final String[] WHITE_LIST = {
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
            "/api/auth/forgot-password", "/api/auth/captcha", "/api/auth/admin/login", "/api/common/",
            // 前台商品浏览无需登录（分类/品牌/列表/详情/热销/商品评价；收藏不在白名单）
            "/api/product/categories", "/api/product/brands", "/api/product/list",
            "/api/product/detail/", "/api/product/hot", "/api/comment/spu/",
            // AI 助手（阶段 9 16.x）：游客普通问答/模型清单无需登录；登录态数据查询后续另行鉴权
            "/api/ai/",
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
        String token = extractToken(exchange.getRequest());
        if (isWhiteListed(path)) {
            // AI 助手（阶段 9 16.3）：游客免登录可直接问答；带 token 的请求也解析登录态并透传用户头
            // （后端据此做能力分层与历史隔离），token 失效则 401（前端静默续期后重放）
            if (path.startsWith("/api/ai/") && token != null) {
                return authenticate(exchange, chain, path);
            }
            return chain.filter(exchange);
        }
        if (token == null) {
            return unauthorized(exchange.getResponse(), "未登录或登录已过期");
        }
        return authenticate(exchange, chain, path);
    }

    /** 校验 token（auth 查 Redis 黑名单）→ 通过后透传用户上下文头，失败 401 */
    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        String token = extractToken(exchange.getRequest());
        return webClientConfig.webClientBuilder().build().post()
                .uri(AUTH_CHECK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    Object code = body.get("code");
                    if (code instanceof Number number && number.intValue() == 200) {
                        Map<?, ?> user = (Map<?, ?>) body.get("data");
                        // 后台路由角色校验：userType 非 ADMIN 拦截（买家 token 不可访问后台管理接口）
                        if (path.startsWith(ADMIN_PATH_PREFIX)
                                && !USER_TYPE_ADMIN.equals(user == null ? null : String.valueOf(user.get("userType")))) {
                            return forbidden(exchange.getResponse(), "无权限访问后台管理接口");
                        }
                        return chain.filter(passUserContext(exchange, user));
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
        return writeError(response, HttpStatus.UNAUTHORIZED, 401,
                StringUtils.hasText(message) ? message : "未登录或登录已过期");
    }

    /** 403 响应（与业务侧 Result 结构一致）：角色不符拦截 */
    private Mono<Void> forbidden(ServerHttpResponse response, String message) {
        return writeError(response, HttpStatus.FORBIDDEN, 403,
                StringUtils.hasText(message) ? message : "无权限访问");
    }

    /** 统一错误响应（401/403） */
    private Mono<Void> writeError(ServerHttpResponse response, HttpStatus status, int code, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
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
