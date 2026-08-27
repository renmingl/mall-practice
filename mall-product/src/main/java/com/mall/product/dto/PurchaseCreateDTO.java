package com.mall.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购单创建 DTO（场景 15.2）
 * @author renmingl
 * @date 2026-08-27 10:30:15
 */
@Data
public class PurchaseCreateDTO {

    private Long supplierId;

    private List<Item> items;

    @Data
    public static class Item {
        private Long skuId;
        private Integer quantity;
        private BigDecimal purchasePrice;
    }
}
