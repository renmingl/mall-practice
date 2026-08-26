package com.mall.common.trace;

import com.mall.common.constant.CommonConstants;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 链路追踪工具（场景 12.9）
 * 职责：MDC 读写 traceId + 生成 traceId。跨服务透传见 {@link com.mall.common.feign.TraceIdFeignInterceptor}；
 * 请求入口写入见 {@link TraceIdServletFilter} / {@link TraceIdReactiveFilter}；
 * 异步线程透传见 {@link MdcTaskWrapper}。
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public final class TraceIdUtil {

    private TraceIdUtil() {
    }

    /**
     * 生成 traceId（32 位无横线 UUID），保证全链路唯一
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 取当前线程 MDC 中的 traceId，无则返回 null
     */
    public static String get() {
        return MDC.get(CommonConstants.TRACE_ID_MDC_KEY);
    }

    /**
     * 取当前线程 MDC 中的 traceId，无则生成并写入 MDC 后返回
     */
    public static String getOrCreate() {
        String traceId = MDC.get(CommonConstants.TRACE_ID_MDC_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generate();
            MDC.put(CommonConstants.TRACE_ID_MDC_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 写入 MDC（透传场景：网关/上游请求头带来的 traceId 写回当前线程）
     */
    public static void put(String traceId) {
        MDC.put(CommonConstants.TRACE_ID_MDC_KEY, traceId);
    }

    /**
     * 清除当前线程 MDC 中的 traceId（过滤器 finally 中调用，防止线程池复用导致串链）
     */
    public static void remove() {
        MDC.remove(CommonConstants.TRACE_ID_MDC_KEY);
    }
}
