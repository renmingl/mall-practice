package com.mall.api.seckill;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 秒杀关单回补请求（Feign 契约 DTO）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
@ToString
public class SeckillReleaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号（幂等键：回补标记 seckill:released:{orderSn}） */
    private String orderSn;

    /** 秒杀商品 ID */
    private Long seckillProductId;

    /** SKU ID（活动结束后回补 sku.stock 用） */
    private Long skuId;

    /** 回补数量 */
    private Integer quantity;

    /** 会员 ID（清理该用户预扣/结果标记） */
    private Long memberId;
}
