package com.mall.product.dto;

import lombok.Data;

/**
 * 分批入库 DTO（场景 15.3，按采购明细收货）
 * @author renmingl
 * @date 2026-08-27 10:30:25
 */
@Data
public class PurchaseReceiveDTO {

    /** 采购明细 ID */
    private Long itemId;

    /** 本次入库数量（累计已入库数 + 本次 ≤ 采购数量） */
    private Integer quantity;
}
