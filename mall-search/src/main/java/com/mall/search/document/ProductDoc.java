package com.mall.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品搜索文档（ES 索引 mall_product，对应 DB product_spu + product_sku 聚合）
 * name/subtitle/categoryName/brandName 参与全文检索与高亮；price 取该 SPU 最低启用 SKU 价
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDoc {

    private Long spuId;
    private String spuCode;
    private String name;
    private String subtitle;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    /** 主图（SPU 首图） */
    private String pic;
    /** 最低启用 SKU 售价（列表展示用） */
    private BigDecimal price;
    private Integer sales;
    private Byte status;
    private String createTime;
}
