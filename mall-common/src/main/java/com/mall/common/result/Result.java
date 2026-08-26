package com.mall.common.result;

import com.mall.common.constant.CommonConstants;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构 Result&lt;T&gt;
 * 所有 Controller 接口一律返回本结构：{@code code} 业务码 + {@code message} 提示 + {@code data} 数据，
 * 前端（或网关）按 code 判断成功与否，与 HTTP 状态码解耦
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：200 成功，其余见 {@link ResultCode} */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 服务端时间戳（毫秒） */
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.BIZ_ERROR.getCode(), message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public boolean isSuccess() {
        return this.code == CommonConstants.SUCCESS_CODE;
    }
}
