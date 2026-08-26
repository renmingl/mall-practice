package com.mall.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关统一 CORS 配置（README 网关职责中的跨域处理在此落地）
 * 开发期前端走 Vite 代理为同源，此配置服务于前后端分离部署场景
 * 说明：allowedOriginPatterns 用通配匹配，配合 allowCredentials 时响应头回显具体 origin（浏览器 CORS 规范禁止 credentials + * 同时使用）
 * @author renmingl
 * @date 2026-08-26 14:30:12
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
