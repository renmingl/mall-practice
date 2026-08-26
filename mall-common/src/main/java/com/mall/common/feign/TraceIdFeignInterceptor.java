package com.mall.common.feign;

import com.mall.common.constant.CommonConstants;
import com.mall.common.trace.TraceIdUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign traceId 透传拦截器：发起 Feign 调用时，把当前线程 MDC 中的 traceId 写入
 * X-Trace-Id 请求头，下游服务的 {@link com.mall.common.trace.TraceIdServletFilter} 取出写回 MDC，
 * 实现跨服务日志串链。
 * 仅在引入 OpenFeign 的服务（mall-portal / mall-admin）生效：依赖为 provided，
 * 用 @ConditionalOnClass 兜底防止类缺失加载失败。
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class TraceIdFeignInterceptor {

    @Bean
    @ConditionalOnMissingBean(RequestInterceptor.class)
    public RequestInterceptor traceIdRequestInterceptor() {
        return template -> {
            String traceId = TraceIdUtil.get();
            if (traceId != null) {
                template.header(CommonConstants.TRACE_ID_HEADER, traceId);
            }
        };
    }
}
