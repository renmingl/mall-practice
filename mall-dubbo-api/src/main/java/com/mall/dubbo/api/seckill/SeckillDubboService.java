package com.mall.dubbo.api.seckill;

/**
 * 秒杀服务 Dubbo 契约（阶段 7 核心链路 RPC：order 落单/关单经 Dubbo 调 seckill）
 * 演进路线第二阶段：核心链路（order → seckill）切换 Dubbo 3，与 Feign 契约（mall-api SeckillFeignClient）双实现共存，
 * 调用方按配置选择（mall.seckill.remote=dubbo|feign）；第三阶段定稿"核心 Dubbo + 边缘 Feign"混合形态
 * 实现侧注解：@DubboService（mall-seckill）；调用侧注解：@DubboReference（mall-order）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
public interface SeckillDubboService {

    /**
     * 核验 Redis 预扣资格（order 秒杀落单前调用，防绕过秒杀入口直接下单）：
     * 校验场次进行中 + 该用户已通过 Lua 预扣（seckill:reserved 存在）+ 返回秒杀价等快照
     * 幂等：同一预扣记录可重复核验，不改变 Redis 状态
     */
    SeckillVerifyResult verifyReservation(Long seckillProductId, Long memberId, Integer quantity);

    /**
     * 秒杀订单关单回补（order 超时关单 0→4 后调用）：
     * 活动进行中 → 回补 Redis 秒杀库存（seckill:stock +1、seckill:reserved 删除）；
     * 活动已结束 → 回补 product_sku.stock（change_type=9 秒杀回补，幂等）
     * 幂等：按 orderSn 在 Redis 记录已回补标记，重复调用跳过
     */
    void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId);
}
