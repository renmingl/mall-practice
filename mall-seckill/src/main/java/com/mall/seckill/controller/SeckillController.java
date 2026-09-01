package com.mall.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.result.Result;
import com.mall.seckill.config.SentinelConfig;
import com.mall.seckill.dto.SeckillSubmitDTO;
import com.mall.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 秒杀前台（14.1 / 14.4 / 14.5 / 14.6）：场次列表、场次商品、秒杀提交（Lua 扣减 + MQ 削峰）、结果轮询、排行榜
 * memberId 从网关透传的 X-User-Id 获取，不信任前端
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /** 场次列表（含进行中/未开始/已结束状态） */
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        return Result.success(seckillService.sessions());
    }

    /** 场次商品列表（预热后读缓存秒开，含剩余库存） */
    @GetMapping("/sessions/{sessionId}/products")
    public Result<List<Map<String, Object>>> products(@PathVariable Long sessionId) {
        return Result.success(seckillService.products(sessionId));
    }

    /** 秒杀排行榜（10.4）：Top N（默认 10） */
    @GetMapping("/sessions/{sessionId}/rank")
    public Result<List<Map<String, Object>>> rank(@PathVariable Long sessionId,
                                                  @RequestParam(defaultValue = "10") int topN) {
        return Result.success(seckillService.rank(sessionId, topN));
    }

    /** 获取秒杀幂等 token（12.3）：进入秒杀页时调用，提交时携带 */
    @GetMapping("/token")
    public Result<Map<String, String>> token(@RequestHeader("X-User-Id") Long memberId) {
        return Result.success(Map.of("token", seckillService.issueToken(memberId)));
    }

    /**
     * 秒杀提交（14.4 / 14.5 / 12.1 / 12.2 / 12.3）：
     * Sentinel 限流（QPS 超限走 blockHandler）+ 防刷 + 幂等 token → Lua 原子扣减 + 限购 → MQ 削峰 → 返回排队，结果轮询
     */
    @SentinelResource(value = SentinelConfig.SECKILL_SUBMIT_RESOURCE,
            blockHandler = "submitBlockHandler", blockHandlerClass = SeckillController.class)
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestHeader("X-User-Id") Long memberId,
                                              @Valid @RequestBody SeckillSubmitDTO dto) {
        return Result.success(seckillService.submit(memberId, dto));
    }

    /** Sentinel 限流降级（12.1）：QPS 超限快速失败，不进入业务逻辑 */
    public static Result<Map<String, Object>> submitBlockHandler(Long memberId, SeckillSubmitDTO dto, BlockException e) {
        return Result.error("抢购人数过多，请稍后再试");
    }

    /** 下单结果查询（14.6）：轮询 status=0 处理中 / 1 成功（orderSn）/ 2 失败（reason） */
    @GetMapping("/result")
    public Result<Map<String, Object>> result(@RequestHeader("X-User-Id") Long memberId,
                                              @RequestParam("seckillProductId") Long seckillProductId) {
        return Result.success(seckillService.queryResult(memberId, seckillProductId));
    }
}
