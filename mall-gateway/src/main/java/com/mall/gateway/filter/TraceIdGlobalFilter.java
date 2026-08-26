package com.mall.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * traceId 全局过滤器（网关零依赖 mall-common，独立实现，与业务服务 TraceIdServletFilter 语义对齐）
 * 职责：请求头 {@code X-Trace-Id} 有值（上游透传）则沿用，无则生成 32 位新值；
 * 回写响应头供前端追踪；写入 MDC 供网关自身日志（pattern 带 %X{traceId}）；
 * 下游服务经网关转发时携带该请求头，业务侧 TraceIdServletFilter 读取后整条链路同 ID。
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    /** 与 mall-common CommonConstants.TRACE_ID_HEADER 保持一致 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final String MDC_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String headerTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        final String traceId = StringUtils.hasText(headerTraceId)
                ? headerTraceId
                : UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, traceId);
        // 响应头回写，前端可按 traceId 定位日志
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        // 请求头透传下游服务
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        // contextWrite 写入 Reactor Context：异步线程切换后（如 WebClient 调用）日志仍能取到 traceId
        // （配合 Hooks.enableAutomaticContextPropagation，MDC 跨线程自动恢复，修复 WebFlux 异步日志丢 traceId）
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(ctx -> ctx.put(MDC_KEY, traceId))
                .doFinally(signalType -> MDC.remove(MDC_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
