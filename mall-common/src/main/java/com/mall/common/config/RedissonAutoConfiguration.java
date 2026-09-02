package com.mall.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redisson 自动配置：基于 spring.data.redis 现有连接信息创建单机 RedissonClient
 * （领券分布式锁用；纯核心库手动装配，避开 redisson-spring-boot-starter 的 Boot 4 适配风险）。
 * 条件装配：只有依赖 common 且显式声明 Redisson 使用（classpath 存在）的服务生效，
 * 未注入 RedissonClient 的服务零开销（Bean 仅创建一次连接池）。
 * 另加 spring.data.redis.host 开关：未配置 Redis 连接的服务不再装配，
 * 避免 Redisson.create 启动即连，导致无 Redis 依赖的服务强制要求 Redis 可达。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedissonAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedissonAutoConfiguration.class);

    /** 单机 RedissonClient（与 StringRedisTemplate 共用同一 Redis 实例，密码等配置保持一致） */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password.isBlank() ? null : password)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4);
        log.info("Redisson 客户端初始化：{}:{}", host, port);
        return Redisson.create(config);
    }
}
