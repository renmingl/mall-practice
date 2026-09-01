package com.mall.api.seckill;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀预扣资格核验结果（Feign 契约 DTO，与 Dubbo 契约 SeckillVerifyResult 独立定义——README 服务间通信）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
@ToString
public class SeckillVerifyResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否核验通过 */
    private boolean ok;

    /** 核验失败原因 */
    private String reason;

    /** 秒杀商品 ID */
    private Long seckillProductId;

    /** 会员 ID */
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

    /** 商品名 */
    private String spuName;

    /** 规格 */
    private String spec;

    /** 商品主图 */
    private String pic;

    /** 成交数量 */
    private Integer quantity;
}
