package com.mall.api.cart;

import lombok.Data;

import java.io.Serializable;

/**
 * 购物车条目（内部契约 DTO：portal 结算预览 / order 下单取勾选条目）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CartItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long skuId;

    /** 数量 */
    private Integer quantity;

    /** 是否勾选：1是 0否 */
    private Boolean checked;
}
