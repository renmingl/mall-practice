package com.mall.common.constant;

/**
 * 公共常量
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** traceId 透传请求头（网关生成 → Feign/HTTP 透传 → 下游取出写回 MDC） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 中 traceId 的 key（与 logback pattern %X{traceId} 对应） */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /** 成功码 */
    public static final int SUCCESS_CODE = 200;
}
