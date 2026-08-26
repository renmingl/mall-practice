package com.mall.common.exception;

import com.mall.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：Service 层业务规则不满足时抛出，由全局异常处理器统一转为 Result JSON
 * 使用示例：{@code throw new BizException("库存不足")} 或 {@code throw new BizException(ResultCode.NOT_FOUND, "订单不存在")}
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务码（默认 1000 业务处理失败，可传 ResultCode 或自定义码） */
    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }
}
