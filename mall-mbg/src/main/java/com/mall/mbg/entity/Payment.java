package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 支付流水ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 支付流水号
     */
    private String paymentSn;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 支付金额
     */
    private BigDecimal payAmount;

    /**
     * 支付方式：1支付宝 2微信
     */
    private Byte payType;

    /**
     * 第三方交易流水号
     */
    private String tradeNo;

    /**
     * 状态：0待支付 1支付成功 2支付失败 3已退款（3=整单全额）
     */
    private Byte status;

    /**
     * 回调通知时间
     */
    private LocalDateTime notifyTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
