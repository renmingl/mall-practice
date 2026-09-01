package com.mall.dubbo.api.seckill;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀预扣资格核验结果（Dubbo 契约 DTO）
 * order 落单前调用 verifyReservation 核验 Redis 预扣资格，返回秒杀快照供建单
 * 说明：DTO 字段只用基本类型/String/BigDecimal，规避 Dubbo 序列化对 JDK8 时间的兼容问题
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
@ToString
public class SeckillVerifyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否核验通过 */
    private boolean ok;

    /** 核验失败原因（ok=false 时返回，如：活动未开始/已结束/无预扣资格） */
    private String reason;

    /** 秒杀商品 ID */
    private Long seckillProductId;

    /** 会员 ID（发起抢购的用户） */
    private Long memberId;

    /** 场次 ID */
    private Long sessionId;

    /** SPU ID */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** SKU 编码（订单明细快照） */
    private String skuCode;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 商品名（SPU 名称快照） */
    private String spuName;

    /** 规格（SKU 规格快照） */
    private String spec;

    /** 商品主图（SKU 图快照） */
    private String pic;

    /** 成交数量（≤ limit_per_user） */
    private Integer quantity;
}
