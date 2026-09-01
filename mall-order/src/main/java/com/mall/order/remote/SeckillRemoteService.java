package com.mall.order.remote;

import com.mall.api.seckill.SeckillVerifyResultDTO;

/**
 * 秒杀远程调用抽象（order → seckill 核心链路）：
 * 演进路线第二阶段——Feign 与 Dubbo 双实现共存，按配置切换（mall.seckill.remote=feign|dubbo，默认 feign）
 * 切换 Dubbo 需 dubbo.enabled=true（mall-seckill 与 mall-order 两侧），Boot 4 未官方适配时保持 feign
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
public interface SeckillRemoteService {

    /** 核验 Redis 预扣资格（防绕过秒杀入口直接下单），失败返回 ok=false + reason */
    SeckillVerifyResultDTO verifyReservation(Long seckillProductId, Long memberId, Integer quantity);

    /** 秒杀订单关单回补（活动进行中回补 Redis 秒杀库存，结束后回补 sku.stock change_type=9；幂等） */
    void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId);
}
