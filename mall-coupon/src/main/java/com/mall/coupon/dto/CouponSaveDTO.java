package com.mall.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板保存请求（后台新增/修改）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CouponSaveDTO {

    /** 券模板 ID（修改时必传） */
    private Long id;

    /** 优惠券名称 */
    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    /** 类型：1满减券 2折扣券 */
    @NotNull(message = "券类型不能为空")
    private Byte type;

    /** 抵扣金额（满减券）/ 折扣率（折扣券，8.5折存0.85） */
    @NotNull(message = "券面值不能为空")
    @DecimalMin(value = "0.01", message = "券面值必须大于 0")
    private BigDecimal amount;

    /** 使用门槛（满 X 元可用，0 为无门槛） */
    private BigDecimal threshold;

    /** 发行总量 */
    @NotNull(message = "发行总量不能为空")
    @Min(value = 1, message = "发行总量至少为 1")
    private Integer totalCount;

    /** 每人限领数量 */
    @NotNull(message = "每人限领不能为空")
    @Min(value = 1, message = "每人限领至少为 1")
    private Integer perLimit;

    /** 生效时间 */
    @NotNull(message = "生效时间不能为空")
    private LocalDateTime useStartTime;

    /** 失效时间 */
    @NotNull(message = "失效时间不能为空")
    private LocalDateTime useEndTime;
}
