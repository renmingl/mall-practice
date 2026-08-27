package com.mall.product.dto;

import lombok.Data;

/**
 * 盘点调整 DTO（场景 15.4，库存流水 change_type=7 报损/报溢）
 * @author renmingl
 * @date 2026-08-27 10:30:20
 */
@Data
public class StockCheckDTO {

    private Long skuId;

    /** 盘点后的实际库存（与当前库存的差额记流水） */
    private Integer stock;

    private String remark;
}
