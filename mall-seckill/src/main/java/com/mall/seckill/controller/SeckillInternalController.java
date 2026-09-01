package com.mall.seckill.controller;

import com.mall.api.seckill.SeckillReleaseDTO;
import com.mall.api.seckill.SeckillVerifyResultDTO;
import com.mall.common.result.Result;
import com.mall.dubbo.api.seckill.SeckillVerifyResult;
import com.mall.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀内部契约（Feign 实现）：order 秒杀落单/关单回补调用（与 Dubbo 契约双实现共存）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/internal/seckill")
@RequiredArgsConstructor
public class SeckillInternalController {

    private final SeckillService seckillService;

    /** 核验 Redis 预扣资格（防绕过秒杀入口直接下单） */
    @GetMapping("/verify-reservation")
    public Result<SeckillVerifyResultDTO> verifyReservation(@RequestParam("seckillProductId") Long seckillProductId,
                                                            @RequestParam("memberId") Long memberId,
                                                            @RequestParam("quantity") Integer quantity) {
        SeckillVerifyResult result = seckillService.verifyReservation(seckillProductId, memberId, quantity);
        SeckillVerifyResultDTO dto = new SeckillVerifyResultDTO();
        dto.setOk(result.isOk());
        dto.setReason(result.getReason());
        dto.setSeckillProductId(result.getSeckillProductId());
        dto.setMemberId(result.getMemberId());
        dto.setSessionId(result.getSessionId());
        dto.setSpuId(result.getSpuId());
        dto.setSkuId(result.getSkuId());
        dto.setSkuCode(result.getSkuCode());
        dto.setSeckillPrice(result.getSeckillPrice());
        dto.setSpuName(result.getSpuName());
        dto.setSpec(result.getSpec());
        dto.setPic(result.getPic());
        dto.setQuantity(result.getQuantity());
        return Result.success(dto);
    }

    /** 秒杀订单关单回补（活动进行中回补 Redis 秒杀库存，结束后回补 sku.stock change_type=9；幂等） */
    @PostMapping("/release-seckill-stock")
    public Result<Void> releaseSeckillStock(@RequestBody SeckillReleaseDTO dto) {
        seckillService.releaseSeckillStock(dto.getOrderSn(), dto.getSeckillProductId(),
                dto.getSkuId(), dto.getQuantity(), dto.getMemberId());
        return Result.success();
    }
}
