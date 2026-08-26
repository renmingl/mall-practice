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
 * 积分流水表
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("member_point_log")
public class MemberPointLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 积分流水ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 变动类型：1注册赠送 2购物返积分 3兑换消耗 4退款扣回
     */
    private Byte changeType;

    /**
     * 变动积分（正数增加，负数扣减）
     */
    private Integer changePoint;

    /**
     * 变动后积分余额
     */
    private Integer pointAfter;

    /**
     * 关联订单号
     */
    private String orderSn;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
