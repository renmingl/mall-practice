package com.mall.auth.exception;

import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证中心安全异常处理器：@PreAuthorize 权限不足统一返回 403（Result JSON）
 * 说明：未认证（AuthenticationException）由网关 401 拦截，本服务仅兜底权限拒绝
 * @author renmingl
 * @date 2026-08-26 20:28:43
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /** 权限不足：RBAC 按钮级权限校验拒绝 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }
}
