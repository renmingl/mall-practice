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
 * 库存流水表（扣减/回补/采购入库/退货入库/盘点对账，防超卖审计）
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_stock_log")
public class ProductStockLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流水ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 业务单号（订单号 / 采购单号 / 退款单号）
     */
    private String bizSn;

    /**
     * 变动类型：1下单扣减 2取消回补 3退款回补 4秒杀扣减 5采购入库 6退货入库 7盘点调整 8人工调整 9秒杀回补
     */
    private Byte changeType;

    /**
     * 变动数量（正数增加、负数减少：入库为正、扣减为负，业务方向看 change_type）
     */
    private Integer changeCount;

    /**
     * 变动前库存
     */
    private Integer stockBefore;

    /**
     * 变动后库存
     */
    private Integer stockAfter;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
