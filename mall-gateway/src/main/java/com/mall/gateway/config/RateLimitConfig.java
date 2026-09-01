package com.mall.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 网关限流 KeyResolver（阶段 8 13.4）：RequestRateLimiter 令牌桶按用户维度限流
 * 优先取认证后透传的 X-User-Id（登录用户按人限流），未登录/白名单接口按客户端 IP 限流
 * @author renmingl
 * @date 2026-09-01 16:10:00
 */
@Configuration
public class RateLimitConfig {

    /** 与 mall-common CommonConstants.USER_ID_HEADER 保持一致（AuthGlobalFilter 写入） */
    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
            if (StringUtils.hasText(userId)) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just("ip:" + ip);
        };
    }
}
