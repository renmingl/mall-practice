package com.mall.api.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单项信息（内部契约 DTO：payment 退款联动查订单明细，随消息投递给 product 回补库存）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class OrderItemInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单项 ID */
    private Long orderItemId;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 成交单价 */
    private BigDecimal price;
}
