package com.mall.seckill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 秒杀商品配置保存 DTO（后台管理，14.2；校验 seckill_stock ≤ sku.stock）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
public class SeckillProductSaveDTO {

    /** 秒杀商品 ID（新增为空） */
    private Long id;

    /** 场次 ID */
    @NotNull(message = "场次必选")
    private Long sessionId;

    /** SKU ID（SPU 由 SKU 反查） */
    @NotNull(message = "SKU 必选")
    private Long skuId;

    /** 秒杀价 */
    @NotNull(message = "秒杀价必填")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
    private BigDecimal seckillPrice;

    /** 秒杀库存（须 ≤ SKU 当前库存） */
    @NotNull(message = "秒杀库存必填")
    @Min(value = 1, message = "秒杀库存至少 1")
    private Integer seckillStock;

    /** 每人限购数量 */
    @NotNull(message = "限购数量必填")
    @Min(value = 1, message = "限购数量至少 1")
    private Integer limitPerUser;

    /** 状态：1启用 0禁用 */
    private Byte status;
}
