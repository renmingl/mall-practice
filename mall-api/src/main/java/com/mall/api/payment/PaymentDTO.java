package com.mall.api.payment;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水（内部契约 DTO：order 收银台跳转、portal 支付结果页查询）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class PaymentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付流水 ID */
    private Long id;

    /** 支付流水号 */
    private String paymentSn;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderSn;

    /** 会员 ID */
    private Long memberId;

    /** 支付金额 */
    private BigDecimal payAmount;

    /** 支付方式：1支付宝 2微信 */
    private Byte payType;

    /** 第三方交易流水号 */
    private String tradeNo;

    /** 状态：0待支付 1支付成功 2支付失败 3已退款 */
    private Byte status;

    /** 回调通知时间 */
    private LocalDateTime notifyTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
