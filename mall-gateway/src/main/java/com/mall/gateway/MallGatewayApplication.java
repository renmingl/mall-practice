package com.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

/**
 * 网关服务启动类（端口 8080）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@SpringBootApplication
public class MallGatewayApplication {

    static {
        // WebFlux 异步场景下 MDC/traceId 跨线程自动传播（Spring Cloud Gateway 官方推荐），
        // 否则转发/WebClient 调用发生在 reactor 线程，MDC 上下文丢失导致日志无 traceId
        Hooks.enableAutomaticContextPropagation();
    }

    public static void main(String[] args) {
        SpringApplication.run(MallGatewayApplication.class, args);
    }
}
