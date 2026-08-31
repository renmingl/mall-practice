package com.mall.api.coupon;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 可用优惠券及优惠金额（内部契约 DTO：portal 结算预览展示、order 锁券计算用）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class CouponAvailableDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户券领取记录 ID（锁券/核销/退回按此定位） */
    private Long couponUserId;

    /** 券模板 ID */
    private Long couponId;

    /** 券名称 */
    private String name;

    /** 类型：1满减券 2折扣券 */
    private Byte type;

    /** 抵扣金额（满减券）/ 折扣率（折扣券） */
    private BigDecimal amount;

    /** 使用门槛（满 X 元可用，0 为无门槛） */
    private BigDecimal threshold;

    /** 该券在本次订单可抵金额（满减=amount；折扣=(1-rate)*totalAmount，封顶不超过商品总额） */
    private BigDecimal discountAmount;
}
