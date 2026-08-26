package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品SKU表（库存随下单扣减，秒杀场景由 Redis 预扣）
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_sku")
public class ProductSku implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SKU ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * 规格属性（JSON，如 {"颜色":"黑","内存":"256G"}）
     */
    private String spec;

    /**
     * 售价
     */
    private BigDecimal price;

    /**
     * 原价/划线价
     */
    private BigDecimal originalPrice;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 库存预警阈值（低于此值触发预警；NULL 取全局默认阈值）
     */
    private Integer lowStock;

    /**
     * SKU 图片
     */
    private String pic;

    /**
     * 销量
     */
    private Integer saleCount;

    /**
     * 乐观锁版本号（防超卖）
     */
    @Version
    private Integer version;

    /**
     * 状态：1启用 0禁用
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
