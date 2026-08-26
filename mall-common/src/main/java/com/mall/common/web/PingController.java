package com.mall.common.web;

import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.common.result.ResultCode;
import com.mall.common.trace.TraceIdUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骨架验证接口（阶段 0）：验证统一返回 Result / 全局异常 / traceId 链路
 * 由 {@code CommonAutoConfiguration} 注册为 Bean，所有 Servlet 业务服务自动生效；
 * 网关为 WebFlux 栈（Reactive），不加载本控制器。
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@RestController
@RequestMapping("/api/common")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PingController {

    /** 存活探针：验证 Result 统一返回结构 */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    /** 异常链路：抛业务异常，验证 GlobalExceptionHandler 统一兜底 */
    @GetMapping("/error")
    public Result<String> error() {
        throw new BizException(ResultCode.BIZ_ERROR, "骨架异常链路验证");
    }

    /** traceId 链路：返回当前请求的 traceId（TraceIdServletFilter 已写入 MDC） */
    @GetMapping("/trace")
    public Result<String> trace() {
        return Result.success("traceId: " + TraceIdUtil.get());
    }
}
