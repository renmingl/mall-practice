package com.mall.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验服务（@PreAuthorize("@ss.hasPerm('system:user:list')") 用，Ruoyi 风格）
 * 超级管理员权限标识 "*"（登录时写入 JWT/透传头），命中即放行全部接口
 * @author renmingl
 * @date 2026-08-26 22:21:31
 */
@Component("ss")
public class SecurityService {

    /** 超级管理员全权限标识 */
    public static final String ALL_PERM = "*";

    /** 当前用户是否拥有指定权限（或为超级管理员） */
    public boolean hasPerm(String perm) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        Set<String> perms = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return perms.contains(ALL_PERM) || perms.contains(perm);
    }

    /** 当前登录用户 ID（网关透传 X-User-Id，JwtAuthFilter 存入 principal；未认证返回 null） */
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof String principal)) {
            return null;
        }
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
