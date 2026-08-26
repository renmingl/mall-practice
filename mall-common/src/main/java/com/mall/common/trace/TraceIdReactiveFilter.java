package com.mall.common.trace;

import com.mall.common.constant.CommonConstants;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * traceId 请求过滤器（Reactive/WebFlux 栈）
 * 与 Servlet 版逻辑一致：取请求头 → 无则生成 → 写 MDC + 响应头 → 清除。
 * 注意：Reactor 线程模型下 MDC 不会跨线程自动透传，本过滤器将 traceId 同时写入
 * Reactor Context（{@code contextWrite}），后续阶段基于 WebClient 调用下游时可从
 * Context 取出放入请求头继续透传。
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdReactiveFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String headerTraceId = exchange.getRequest().getHeaders().getFirst(CommonConstants.TRACE_ID_HEADER);
        String traceId = (headerTraceId == null || headerTraceId.isEmpty()) ? TraceIdUtil.generate() : headerTraceId;
        MDC.put(CommonConstants.TRACE_ID_MDC_KEY, traceId);
        exchange.getResponse().getHeaders().set(CommonConstants.TRACE_ID_HEADER, traceId);
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(CommonConstants.TRACE_ID_MDC_KEY, traceId))
                .doFinally(signalType -> TraceIdUtil.remove());
    }
}
