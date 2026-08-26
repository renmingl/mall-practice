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
 * 退款单表（整单退款状态机；退款成功后回补库存、退回优惠券——退券校验有效期，过期置已过期）
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("payment_refund")
public class PaymentRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 退款单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 退款单号
     */
    private String refundSn;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 支付流水号
     */
    private String paymentSn;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 退款金额（整单退款，等于订单实付）
     */
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    private String reason;

    /**
     * 退款类型：1仅退款 2退货退款（整单退款）
     */
    private Byte refundType;

    /**
     * 退货物流公司（退货退款用）
     */
    private String returnCompany;

    /**
     * 退货物流单号（退货退款用）
     */
    private String returnSn;

    /**
     * 状态：0申请中 1审核通过 2退货中 3退款中 4已退款 5已拒绝（仅退款跳过 2；第三方退款失败停留 3，重试/人工介入）
     */
    private Byte status;

    /**
     * 审核人（后台审核退款申请）
     */
    private String auditBy;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    /**
     * 申请时间
     */
    private LocalDateTime applyTime;

    /**
     * 退款到账时间
     */
    private LocalDateTime refundTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
