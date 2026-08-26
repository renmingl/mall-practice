package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单明细表（进销存-进）
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_purchase_item")
public class ProductPurchaseItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 采购明细ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 采购单ID
     */
    private Long purchaseId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 采购数量
     */
    private Integer quantity;

    /**
     * 已入库数量（分批收货累计）
     */
    private Integer receivedQuantity;

    /**
     * 采购单价
     */
    private BigDecimal purchasePrice;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
