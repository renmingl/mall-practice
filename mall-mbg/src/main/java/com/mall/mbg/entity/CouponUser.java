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
 * 用户优惠券领取/使用记录表
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("coupon_user")
public class CouponUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 领取记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 核销订单ID
     */
    private Long orderId;

    /**
     * 状态：0未使用 1已锁定（下单占用） 2已使用 3已过期（取消/超时关单由1回退到0，退款退回由2回退到0；退回时校验券有效期，已过期则置3）
     */
    private Byte status;

    /**
     * 领取时间
     */
    private LocalDateTime receiveTime;

    /**
     * 锁定时间（下单占用）
     */
    private LocalDateTime lockTime;

    /**
     * 使用时间
     */
    private LocalDateTime useTime;
}
