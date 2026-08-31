package com.mall.api.payment;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建支付单请求（内部契约 DTO：order 拉起收银台前调 payment 创建支付流水）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CreatePaymentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderSn;

    /** 会员 ID */
    private Long memberId;

    /** 支付金额（= 订单实付 payAmount） */
    private BigDecimal payAmount;

    /** 支付方式：1支付宝 2微信 */
    private Byte payType;
}
