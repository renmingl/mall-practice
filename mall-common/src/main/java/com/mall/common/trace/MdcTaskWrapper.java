package com.mall.common.trace;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * MDC 透传包装工具：线程池 / MQ 消费线程不继承父线程 MDC，提交任务时用本工具包装，
 * 子线程执行时先恢复父线程的 traceId，执行完恢复子线程原上下文，避免串链。
 * 使用示例：
 * {@code
 * executor.execute(MdcTaskWrapper.wrap(() -> log.info("异步任务，traceId 已透传")));
 * executor.execute(MdcTaskWrapper.wrap(new Runnable() { ... }));
 * }
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public final class MdcTaskWrapper {

    private MdcTaskWrapper() {
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            if (contextMap == null || contextMap.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(contextMap);
            }
            try {
                task.run();
            } finally {
                if (backup == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(backup);
                }
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> backup = MDC.getCopyOfContextMap();
            if (contextMap == null || contextMap.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(contextMap);
            }
            try {
                return task.call();
            } finally {
                if (backup == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(backup);
                }
            }
        };
    }

    /**
     * 包装 Executor：submit/execute 的 Runnable/Callable 自动携带父线程 traceId
     */
    public static Executor wrap(Executor executor) {
        return command -> executor.execute(wrap(command));
    }
}
