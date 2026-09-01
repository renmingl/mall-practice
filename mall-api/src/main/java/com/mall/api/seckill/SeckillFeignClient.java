package com.mall.api.seckill;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 秒杀服务内部契约（order 秒杀落单/关单回补调用，与 Dubbo 契约 mall-dubbo-api 双实现共存）
 * 演进路线：核心链路（order → seckill）先 Feign 走通，再切换 Dubbo（mall.seckill.remote=dubbo|feign）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@FeignClient(name = "mall-seckill", path = "/internal/seckill", contextId = "seckillFeignClient")
public interface SeckillFeignClient {

    /** 核验 Redis 预扣资格（防绕过秒杀入口直接下单），失败返回 ok=false + reason */
    @GetMapping("/verify-reservation")
    Result<SeckillVerifyResultDTO> verifyReservation(@RequestParam("seckillProductId") Long seckillProductId,
                                                     @RequestParam("memberId") Long memberId,
                                                     @RequestParam("quantity") Integer quantity);

    /** 秒杀订单关单回补（活动进行中回补 Redis 秒杀库存，结束后回补 sku.stock change_type=9；幂等） */
    @PostMapping("/release-seckill-stock")
    Result<Void> releaseSeckillStock(@RequestBody SeckillReleaseDTO dto);
}
