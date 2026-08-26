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
 * 订单表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 幂等键（客户端生成，防重复提交，下单必传）
     */
    private String requestId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 订单类型：1普通 2秒杀
     */
    private Byte orderType;

    /**
     * 会员名（下单快照）
     */
    private String memberName;

    /**
     * 商品总额
     */
    private BigDecimal totalAmount;

    /**
     * 运费
     */
    private BigDecimal freightAmount;

    /**
     * 优惠券抵扣
     */
    private BigDecimal couponAmount;

    /**
     * 其他优惠（秒杀/满减等）
     */
    private BigDecimal discountAmount;

    /**
     * 应付金额（实付）
     */
    private BigDecimal payAmount;

    /**
     * 支付方式：1支付宝 2微信
     */
    private Byte payType;

    /**
     * 状态：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款（5=整单全额退款）
     */
    private Byte status;

    /**
     * 收货人（下单快照）
     */
    private String receiverName;

    /**
     * 收货电话
     */
    private String receiverPhone;

    /**
     * 收货地址（省市区+详细，拼接快照）
     */
    private String receiverAddress;

    /**
     * 买家备注
     */
    private String remark;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 发货物流公司（后台发货填写）
     */
    private String deliveryCompany;

    /**
     * 发货物流单号
     */
    private String deliverySn;

    /**
     * 发货时间
     */
    private LocalDateTime deliveryTime;

    /**
     * 收货时间
     */
    private LocalDateTime receiveTime;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
