package com.mall.product.dto;

import com.mall.mbg.entity.ProductSku;
import lombok.Data;

import java.util.List;

/**
 * 商品保存 DTO：SPU 字段 + SKU 列表（场景 2.2 后台商品编辑）
 * @author renmingl
 * @date 2026-08-27 10:30:10
 */
@Data
public class SpuSaveDTO {

    /** SPU ID（null 为新增） */
    private Long id;

    private String spuCode;

    private Long categoryId;

    private Long brandId;

    private String name;

    private String subtitle;

    private String mainPic;

    private String pics;

    private String unit;

    private String detail;

    /** 状态：0下架 1上架 */
    private Integer status;

    private Integer sort;

    /** SKU 列表（修改时全量重建，库存以原值为准） */
    private List<ProductSku> skuList;
}
