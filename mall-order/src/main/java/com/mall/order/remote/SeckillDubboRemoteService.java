package com.mall.order.remote;

import com.mall.api.seckill.SeckillVerifyResultDTO;
import com.mall.dubbo.api.seckill.SeckillDubboService;
import com.mall.dubbo.api.seckill.SeckillVerifyResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 秒杀远程调用 Dubbo 实现（演进路线第二阶段）：核心链路 order→seckill 切换 Dubbo 3（Triple）
 * 装配条件：mall.seckill.remote=dubbo 且 dubbo.enabled=true（Boot 4 官方未声明适配，启动异常时改回 feign）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mall.seckill.remote", havingValue = "dubbo")
@ConditionalOnProperty(name = "dubbo.enabled", havingValue = "true", matchIfMissing = true)
public class SeckillDubboRemoteService implements SeckillRemoteService {

    @DubboReference(check = false, timeout = 5000)
    private SeckillDubboService seckillDubboService;

    @Override
    public SeckillVerifyResultDTO verifyReservation(Long seckillProductId, Long memberId, Integer quantity) {
        SeckillVerifyResult result = seckillDubboService.verifyReservation(seckillProductId, memberId, quantity);
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
        return dto;
    }

    @Override
    public void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId) {
        seckillDubboService.releaseSeckillStock(orderSn, seckillProductId, skuId, quantity, memberId);
    }
}
