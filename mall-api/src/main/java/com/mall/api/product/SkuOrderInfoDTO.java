package com.mall.api.product;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 下单快照（内部契约 DTO：order 校验商品、cart 组装列表用）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class SkuOrderInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long skuId;

    /** SKU 编码 */
    private String skuCode;

    /** SPU ID */
    private Long spuId;

    /** SPU 名称（下单快照） */
    private String spuName;

    /** 规格描述 */
    private String spec;

    /** 商品图片（SKU 优先，空取 SPU 主图） */
    private String pic;

    /** 售价 */
    private BigDecimal price;

    /** 当前库存 */
    private Integer stock;

    /** SKU 状态：1启用 0禁用 */
    private Byte status;

    /** SPU 状态：1上架 0下架 */
    private Byte spuStatus;
}
