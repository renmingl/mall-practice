package com.mall.common.result;

import lombok.Getter;

/**
 * 统一返回码
 * 2xx 与 HTTP 语义对齐；1000+ 为业务码，随业务场景扩展（各服务可自定义业务码段）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),

    // ---------- 业务码段（1000+） ----------
    BIZ_ERROR(1000, "业务处理失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
