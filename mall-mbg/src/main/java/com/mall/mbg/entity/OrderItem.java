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
 * 订单项表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("order_item")
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单项ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号（冗余，便于查询）
     */
    private String orderSn;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * 商品名称（下单快照）
     */
    private String spuName;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * 规格描述（下单快照）
     */
    private String spec;

    /**
     * 商品图片（下单快照）
     */
    private String pic;

    /**
     * 成交单价（下单快照）
     */
    private BigDecimal price;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 小计（price*quantity）
     */
    private BigDecimal subtotal;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
