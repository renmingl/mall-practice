package com.mall.order.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀落单消息体（seckill 提交后经 MQ 削峰投递，order 异步建单，14.5）
 * 收货信息由秒杀页提交时携带（member 服务无地址模块），落单时快照到订单
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Data
public class SeckillOrderMsg implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀商品 ID */
    private Long seckillProductId;

    /** 场次 ID */
    private Long sessionId;

    /** 会员 ID */
    private Long memberId;

    /** 购买数量 */
    private Integer quantity;

    /** 幂等键（防重复落单/重复扣减） */
    private String requestId;

    /** 收货人 */
    private String receiverName;

    /** 收货电话 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;
}
