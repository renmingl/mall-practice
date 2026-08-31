package com.mall.api.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单信息（内部契约 DTO：payment 创建支付单/申请退款校验时查订单用）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class OrderInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 订单号 */
    private String orderSn;

    /** 会员 ID */
    private Long memberId;

    /** 商品总额 */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 优惠券抵扣 */
    private BigDecimal couponAmount;

    /** 应付金额（实付） */
    private BigDecimal payAmount;

    /** 支付方式：1支付宝 2微信 */
    private Byte payType;

    /** 状态：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款 */
    private Byte status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 收货时间 */
    private LocalDateTime receiveTime;
}
