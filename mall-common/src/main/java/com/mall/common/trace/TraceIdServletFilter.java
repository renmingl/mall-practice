package com.mall.common.trace;

import com.mall.common.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * traceId 请求过滤器（Servlet 栈，业务服务使用）
 * 逻辑：取请求头 X-Trace-Id（网关/上游透传）→ 无则生成 → 写入 MDC → 响应头回写 →
 * finally 清除 MDC（防止 Tomcat 线程池复用导致 traceId 串到下一个请求）。
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdServletFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(CommonConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdUtil.generate();
        }
        MDC.put(CommonConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(CommonConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdUtil.remove();
        }
    }
}
