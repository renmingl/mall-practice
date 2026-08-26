package com.mall.auth.config;

import com.mall.auth.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（1.3 网关鉴权 vs 业务服务鉴权）：
 * 网关负责 token 有效性校验并透传 X-User-Id/X-User-Type/X-User-Perms，
 * 本服务 JwtAuthFilter 据透传头构造认证上下文，接口用 @PreAuthorize 做权限校验（1.9）
 * @author renmingl
 * @date 2026-08-26 20:39:13
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录/注册/验证码/骨架验证/内部契约/接口文档/健康检查放行（后台 /me 需认证，防透传头伪造）
                        // 注意：/api/auth/captcha 与子路径 /sms 需用通配符（精确匹配不覆盖子路径）
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                                "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/captcha/**",
                                "/api/auth/admin/login", "/api/common/**", "/internal/**", "/doc.html",
                                "/webjars/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        // 其余（后台管理接口/当前用户信息等）需网关鉴权透传头（JwtAuthFilter 构造认证）
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
