package com.mall.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 后台管理接口权限过滤器（Servlet 栈业务服务兜底防护）
 * 网关 AuthGlobalFilter 已对 /api/admin/** 校验 userType=ADMIN；本过滤器在业务服务侧
 * 再次校验 X-User-Type 头，防止内网直连/伪造同名头绕过网关直接访问后台接口。
 * 注册见 CommonAutoConfiguration（FilterRegistrationBean 拦截 /api/admin/*）。
 * 内部契约接口（/internal/**）不受影响；admin 登录（/api/auth/admin/**）不在拦截前缀内。
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
public class AdminApiAuthFilter extends OncePerRequestFilter {

    /** 后台管理员用户类型（与 mall-auth JwtUtil.USER_TYPE_ADMIN 约定一致） */
    public static final String USER_TYPE_ADMIN = "ADMIN";

    /** 用户类型透传头（与 mall-gateway AuthGlobalFilter.HEADER_USER_TYPE 约定一致） */
    public static final String HEADER_USER_TYPE = "X-User-Type";

    private final ObjectMapper objectMapper;

    public AdminApiAuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (USER_TYPE_ADMIN.equals(request.getHeader(HEADER_USER_TYPE))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ResultCode.FORBIDDEN)));
    }
}
