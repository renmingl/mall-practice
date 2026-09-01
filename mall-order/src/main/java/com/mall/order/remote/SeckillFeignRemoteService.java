package com.mall.order.remote;

import com.mall.api.seckill.SeckillFeignClient;
import com.mall.api.seckill.SeckillReleaseDTO;
import com.mall.api.seckill.SeckillVerifyResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 秒杀远程调用 Feign 实现（默认）：演进路线第一阶段（全 Feign 走通），Boot 4 下零适配风险
 * 切换 Dubbo：mall.seckill.remote=dubbo（且 dubbo.enabled=true）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.seckill.remote", havingValue = "feign", matchIfMissing = true)
public class SeckillFeignRemoteService implements SeckillRemoteService {

    private final SeckillFeignClient seckillFeignClient;

    @Override
    public SeckillVerifyResultDTO verifyReservation(Long seckillProductId, Long memberId, Integer quantity) {
        return seckillFeignClient.verifyReservation(seckillProductId, memberId, quantity).getDataOrThrow();
    }

    @Override
    public void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId) {
        SeckillReleaseDTO dto = new SeckillReleaseDTO();
        dto.setOrderSn(orderSn);
        dto.setSeckillProductId(seckillProductId);
        dto.setSkuId(skuId);
        dto.setQuantity(quantity);
        dto.setMemberId(memberId);
        seckillFeignClient.releaseSeckillStock(dto).getDataOrThrow();
    }
}
