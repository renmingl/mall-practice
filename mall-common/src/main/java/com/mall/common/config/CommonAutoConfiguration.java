package com.mall.common.config;

import com.mall.common.exception.GlobalExceptionHandler;
import com.mall.common.id.SnowflakeIdGenerator;
import com.mall.common.trace.TraceIdReactiveFilter;
import com.mall.common.trace.TraceIdServletFilter;
import com.mall.common.web.PingController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * mall-common 自动配置：traceId 过滤器（Servlet/Reactive 按应用类型二选一）、
 * 全局异常处理器、雪花 ID 生成器。
 * 注册入口：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports。
 * 网关（WebFlux，不依赖 mall-common）自行实现等价过滤器，见 mall-gateway 模块。
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@AutoConfiguration
@ConditionalOnWebApplication
// MallIdProperties（mall.id.*）在此注册绑定：否则手动 new 的 Bean 不会读取 yml 配置
@EnableConfigurationProperties(MallIdProperties.class)
public class CommonAutoConfiguration {

    /** Servlet 栈（业务服务）：traceId 过滤器，最高优先级最先执行 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<TraceIdServletFilter> traceIdServletFilter() {
        FilterRegistrationBean<TraceIdServletFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdServletFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("traceIdFilter");
        return registration;
    }

    /** Reactive 栈（WebFlux 服务）：traceId 过滤器 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public TraceIdReactiveFilter traceIdReactiveFilter() {
        return new TraceIdReactiveFilter();
    }

    /** 全局异常处理器：统一捕获异常返回 Result JSON */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** 骨架验证接口（仅 Servlet 栈）：/api/common/ping|error|trace，网关 Reactive 不加载 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PingController pingController() {
        return new PingController();
    }

    /** 雪花 ID 生成器：workerId/datacenterId 由 mall.id.* 配置（经 @EnableConfigurationProperties 绑定），默认 0 */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(MallIdProperties properties) {
        return new SnowflakeIdGenerator(properties.getWorkerId(), properties.getDatacenterId());
    }
}
