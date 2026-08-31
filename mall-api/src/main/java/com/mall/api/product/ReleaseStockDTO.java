package com.mall.api.product;

import lombok.Data;

import java.io.Serializable;

/**
 * 回补库存请求（内部契约 DTO：order 取消/超时关单、payment 退款联动调用）
 * change_type：2取消回补 3退款回补 9秒杀回补
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class ReleaseStockDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务单号（订单号，库存流水 biz_sn 对账用） */
    private String bizSn;

    /** SKU ID */
    private Long skuId;

    /** 回补数量（正整数） */
    private Integer quantity;

    /** 变动类型：2取消回补 3退款回补 9秒杀回补 */
    private Integer changeType;
}
