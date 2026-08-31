package com.mall.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请退款请求（订单详情页发起，整单退款）
 * 仅退款（refundType=1）无需退货物流；退货退款（refundType=2）须填退货物流
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class RefundApplyDTO {

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderSn;

    /** 退款原因 */
    private String reason;

    /** 退款类型：1仅退款 2退货退款（整单退款） */
    @NotNull(message = "退款类型必填")
    private Byte refundType;

    /** 退货物流公司（退货退款必填） */
    private String returnCompany;

    /** 退货物流单号（退货退款必填） */
    private String returnSn;
}
