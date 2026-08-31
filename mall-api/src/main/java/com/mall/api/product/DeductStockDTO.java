package com.mall.api.product;

import lombok.Data;

import java.io.Serializable;

/**
 * 扣减库存请求（内部契约 DTO：order 下单调用，change_type=1 下单扣减）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class DeductStockDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务单号（订单号，库存流水 biz_sn 对账用） */
    private String bizSn;

    /** SKU ID */
    private Long skuId;

    /** 扣减数量（正整数） */
    private Integer quantity;
}
