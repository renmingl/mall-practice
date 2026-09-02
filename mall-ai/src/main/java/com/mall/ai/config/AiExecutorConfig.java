package com.mall.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 模块线程池：SSE 流式问答专用（阶段 9 16.3）
 * 不占用 ForkJoinPool.commonPool：上游模型单次响应最长 60s，并发流式请求会把
 * commonPool 占满，波及其他 CompletableFuture / ForkJoin 任务（JDK 9+ commonPool
 * 仅 CPU 核数-1 条线程），故独立池化并按流式任务特征配置容量
 * @author renmingl
 * @date 2026-09-02 17:30:00
 */
@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiSseExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-sse-");
        // 应用停止时等待在途流式任务收尾，避免推送半截
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
