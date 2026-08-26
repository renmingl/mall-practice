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
 * 采购单表（进销存-进）
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("product_purchase")
public class ProductPurchase implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 采购单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 采购单号（雪花ID，幂等）
     */
    private String purchaseSn;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 采购总金额（采购价 x 数量）
     */
    private BigDecimal totalAmount;

    /**
     * 状态：0待审核 1待收货 2部分入库 3已完成 4已取消
     */
    private Byte status;

    /**
     * 审核人
     */
    private String auditBy;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
