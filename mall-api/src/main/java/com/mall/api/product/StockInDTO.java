package com.mall.api.product;

import lombok.Data;

import java.io.Serializable;

/**
 * 入库请求（内部契约 DTO：payment 退款退货入库调用，change_type=6 退货入库）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class StockInDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务单号（退款单号，库存流水 biz_sn 对账用） */
    private String bizSn;

    /** SKU ID */
    private Long skuId;

    /** 入库数量（正整数） */
    private Integer quantity;

    /** 变动类型：6退货入库 */
    private Integer changeType;
}
