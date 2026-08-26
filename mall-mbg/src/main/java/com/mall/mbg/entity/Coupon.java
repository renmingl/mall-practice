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
 * 优惠券表
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券名称
     */
    private String name;

    /**
     * 类型：1满减券 2折扣券
     */
    private Byte type;

    /**
     * 抵扣金额（满减券）/ 折扣率（折扣券，如8.5折存0.85）
     */
    private BigDecimal amount;

    /**
     * 使用门槛（满X元可用，0为无门槛）
     */
    private BigDecimal threshold;

    /**
     * 发行总量
     */
    private Integer totalCount;

    /**
     * 每人限领数量
     */
    private Integer perLimit;

    /**
     * 已领取数量
     */
    private Integer receivedCount;

    /**
     * 生效时间
     */
    private LocalDateTime useStartTime;

    /**
     * 失效时间
     */
    private LocalDateTime useEndTime;

    /**
     * 状态：1进行中 0已结束
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
