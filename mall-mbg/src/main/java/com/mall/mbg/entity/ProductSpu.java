package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品SPU表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_spu")
public class ProductSpu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SPU ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * SPU 编码（商品编码，与 SKU 编码对称）
     */
    private String spuCode;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 副标题/卖点
     */
    private String subtitle;

    /**
     * 主图
     */
    private String mainPic;

    /**
     * 轮播图集合（JSON数组）
     */
    private String pics;

    /**
     * 计量单位
     */
    private String unit;

    /**
     * 商品详情（富文本HTML）
     */
    private String detail;

    /**
     * 累计销量
     */
    private Integer sales;

    /**
     * 状态：0下架 1上架
     */
    private Byte status;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
