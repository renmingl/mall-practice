package com.mall.seckill.service.impl;

import com.mall.dubbo.api.seckill.SeckillDubboService;
import com.mall.dubbo.api.seckill.SeckillVerifyResult;
import com.mall.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 秒杀 Dubbo 服务实现（阶段 7 核心链路 RPC）：order 落单核验预扣资格 / 关单回补
 * 与 Feign 契约（SeckillInternalController）双实现共存，调用方按 mall.seckill.remote 切换
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class SeckillDubboServiceImpl implements SeckillDubboService {

    private final SeckillService seckillService;

    @Override
    public SeckillVerifyResult verifyReservation(Long seckillProductId, Long memberId, Integer quantity) {
        return seckillService.verifyReservation(seckillProductId, memberId, quantity);
    }

    @Override
    public void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId) {
        seckillService.releaseSeckillStock(orderSn, seckillProductId, skuId, quantity, memberId);
    }
}
