package com.mall.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.api.product.ProductFeignClient;
import com.mall.api.product.ReleaseStockDTO;
import com.mall.api.product.SkuOrderInfoDTO;
import com.mall.common.exception.BizException;
import com.mall.common.mq.MqSender;
import com.mall.common.mq.MqTopics;
import com.mall.dubbo.api.seckill.SeckillVerifyResult;
import com.mall.mbg.entity.SeckillProduct;
import com.mall.mbg.entity.SeckillSession;
import com.mall.seckill.constant.SeckillConstants;
import com.mall.seckill.dto.SeckillSubmitDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀核心服务（14.3～14.6 + 8.3 + 9.6 + 10.4 + 12.1～12.3）：
 * 预热（DB→Redis，校验 seckill_stock ≤ sku.stock）→ Lua 原子扣减 + 限购（防超卖/防黄牛）
 * → 防刷 + 幂等 token → MQ 削峰异步下单（SECKILL→ORDER）→ 结果轮询；
 * 核验预扣资格（order 落单前，防绕过秒杀入口）与关单回补（活动进行中回补 Redis、结束后回补 sku.stock）经 Dubbo/Feign 双契约暴露
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MqSender mqSender;
    private final SeckillSessionService sessionService;
    private final SeckillProductService productService;
    private final ProductFeignClient productFeignClient;

    // ==================== 预热（14.3） ====================

    /**
     * 预热场次：校验配置（seckill_stock ≤ sku.stock）→ 秒杀库存写入 Redis（覆盖式，可重复预热）
     * → 清理上一周期限购/预扣标记 → 缓存场次信息与商品列表（商品列表带 SKU 快照，供会场页秒开）
     * 预热后场次开始前用户可见剩余库存，开始瞬间即可 Lua 扣减（库存已在内存中）
     */
    public void preheat(Long sessionId) {
        SeckillSession session = sessionService.getById(sessionId);
        if (session.getStatus() == 0) {
            throw new BizException("场次已禁用，不可预热");
        }
        List<SeckillProduct> products = productService.listEnabled(sessionId);
        if (products.isEmpty()) {
            throw new BizException("场次下没有启用的秒杀商品，无法预热");
        }
        List<Map<String, Object>> cachedProducts = new ArrayList<>();
        for (SeckillProduct p : products) {
            SkuOrderInfoDTO sku = productFeignClient.getSkuOrderInfo(p.getSkuId()).getDataOrThrow();
            if (p.getSeckillStock() > sku.getStock()) {
                throw new BizException("秒杀商品 " + p.getSkuId() + " 库存配置超过 SKU 当前库存，请先补货或调整配置");
            }
            redisTemplate.opsForValue().set(stockKey(p.getId()), String.valueOf(p.getSeckillStock()));
            // 限购/预扣按 memberId 维度隔离，按 pattern 通配清理上一周期残留（SCAN 分批，不用 KEYS）
            scanDelete(SeckillConstants.KEY_LIMIT + p.getId() + ":*");
            scanDelete(SeckillConstants.KEY_RESERVED + p.getId() + ":*");
            // 注：result key 不主动清理，新结果覆盖 + 30 分钟 TTL 自然过期
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("sessionId", p.getSessionId());
            row.put("spuId", p.getSpuId());
            row.put("skuId", p.getSkuId());
            row.put("spuName", sku.getSpuName());
            row.put("spec", sku.getSpec());
            row.put("pic", sku.getPic());
            row.put("price", sku.getPrice());
            row.put("seckillPrice", p.getSeckillPrice());
            row.put("seckillStock", p.getSeckillStock());
            row.put("limitPerUser", p.getLimitPerUser());
            cachedProducts.add(row);
        }
        redisTemplate.opsForValue().set(sessionKey(sessionId), toJson(session));
        redisTemplate.opsForValue().set(productsKey(sessionId), toJson(cachedProducts));
        // 场次结束后清空 Redis 秒杀态（活动结束时间 + 1 小时兜底，防时钟漂移）
        long ttlSeconds = Duration.between(LocalDateTime.now(), session.getEndTime()).toSeconds() + 3600;
        redisTemplate.expire(sessionKey(sessionId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(productsKey(sessionId), ttlSeconds, TimeUnit.SECONDS);
        log.info("秒杀预热完成 sessionId={} products={} ttl={}s", sessionId, cachedProducts.size(), ttlSeconds);
    }

    /** 定时预热：扫描即将开始（10 分钟内）且已预热过标记不在的启用场次 */
    public int preheatUpcoming() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillSession> upcoming = sessionService.listEnabledSessions(now.plusMinutes(SeckillConstants.PREHEAT_AHEAD_MINUTES), now);
        int count = 0;
        for (SeckillSession session : upcoming) {
            String flag = "seckill:preheated:" + session.getId();
            Boolean first = redisTemplate.opsForValue().setIfAbsent(flag, "1", Duration.ofMinutes(30));
            if (!Boolean.TRUE.equals(first)) {
                continue; // 已预热或预热中
            }
            try {
                preheat(session.getId());
                count++;
            } catch (Exception e) {
                log.error("定时预热失败 sessionId={}", session.getId(), e);
                redisTemplate.delete(flag);
            }
        }
        return count;
    }

    // ==================== 前台查询 ====================

    /** 前台场次列表（含进行中状态） */
    public List<Map<String, Object>> sessions() {
        return sessionService.listActive();
    }

    /** 场次商品列表：优先读预热缓存（含剩余库存），未预热回源 DB 组装 */
    public List<Map<String, Object>> products(Long sessionId) {
        String cached = redisTemplate.opsForValue().get(productsKey(sessionId));
        if (cached != null) {
            try {
                List<Map<String, Object>> list = objectMapper.readValue(cached, List.class);
                for (Map<String, Object> row : list) {
                    String stock = redisTemplate.opsForValue().get(stockKey(Long.valueOf(String.valueOf(row.get("id")))));
                    row.put("remainStock", stock == null ? 0 : Integer.parseInt(stock));
                }
                return list;
            } catch (Exception e) {
                log.warn("场次商品缓存反序列化失败 sessionId={}，回源 DB", sessionId, e);
            }
        }
        List<Map<String, Object>> list = productService.listBySession(sessionId);
        for (Map<String, Object> row : list) {
            Long pid = Long.valueOf(String.valueOf(row.get("id")));
            String stock = redisTemplate.opsForValue().get(stockKey(pid));
            row.put("remainStock", stock == null ? 0 : Integer.parseInt(stock));
        }
        return list;
    }

    /** 秒杀排行榜（10.4）：ZSET rank，member=skuId，score=成交数，Top N */
    public List<Map<String, Object>> rank(Long sessionId, int topN) {
        String key = SeckillConstants.KEY_RANK + sessionId;
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1L);
        List<Map<String, Object>> result = new ArrayList<>();
        if (entries == null) {
            return result;
        }
        List<Long> skuIds = new ArrayList<>();
        for (var entry : entries) {
            skuIds.add(Long.valueOf(entry.getValue()));
        }
        Map<Long, SkuOrderInfoDTO> skuMap = new LinkedHashMap<>();
        if (!skuIds.isEmpty()) {
            for (SkuOrderInfoDTO sku : productFeignClient.getSkuOrderInfos(skuIds).getDataOrThrow()) {
                skuMap.put(sku.getSkuId(), sku);
            }
        }
        for (var entry : entries) {
            Long skuId = Long.valueOf(entry.getValue());
            SkuOrderInfoDTO sku = skuMap.get(skuId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", skuId);
            row.put("sales", entry.getScore().longValue());
            if (sku != null) {
                row.put("spuName", sku.getSpuName());
                row.put("pic", sku.getPic());
                row.put("price", sku.getPrice());
            }
            result.add(row);
        }
        return result;
    }

    // ==================== 秒杀提交（14.4 / 14.5 / 12.1 / 12.2 / 12.3） ====================

    /**
     * 秒杀下单：防刷 → 幂等 token → 场次校验 → Lua 原子扣减 + 限购 → MQ 削峰异步下单
     * 前端快速失败（未抢到立即返回），成功返回排队标识，下单结果经轮询获取（14.6）
     */
    public Map<String, Object> submit(Long memberId, SeckillSubmitDTO dto) {
        // 12.2 接口防刷：同一用户固定窗口（1 秒）内最多 3 次提交
        String antispamKey = SeckillConstants.KEY_ANTISPAM + memberId;
        Long count = redisTemplate.opsForValue().increment(antispamKey);
        if (count != null && count == 1) {
            redisTemplate.expire(antispamKey, SeckillConstants.ANTISPAM_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > SeckillConstants.ANTISPAM_MAX) {
            throw new BizException("操作过于频繁，请稍后再试");
        }
        // 12.3 幂等 token：秒杀页进入时获取，提交时原子校验删除（防重复点击/重放）
        if (dto.getToken() == null || dto.getToken().isBlank()) {
            throw new BizException("请先获取秒杀令牌");
        }
        Long tokenOk = redisTemplate.execute(SeckillConstants.LUA_CONSUME_TOKEN_SCRIPT,
                List.of(tokenKey(memberId)), dto.getToken());
        if (!Long.valueOf(1).equals(tokenOk)) {
            throw new BizException("秒杀令牌无效或已使用，请刷新页面");
        }
        SeckillProduct product = productService.getById(dto.getSeckillProductId());
        SeckillSession session = sessionService.getById(product.getSessionId());
        if (!sessionService.isOngoing(session)) {
            throw new BizException("秒杀活动未开始或已结束");
        }
        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity > product.getLimitPerUser()) {
            throw new BizException("超过每人限购数量（限购 " + product.getLimitPerUser() + " 件）");
        }
        // 9.6 Lua 原子扣减 + 限购（防超卖 / 防黄牛；limit/reserved 均按 memberId 维度隔离）
        Long result = redisTemplate.execute(SeckillConstants.LUA_DEDUCT_SCRIPT,
                List.of(stockKey(product.getId()), limitKey(product.getId(), memberId), reservedKey(product.getId(), memberId)),
                String.valueOf(quantity), String.valueOf(product.getLimitPerUser()));
        if (Long.valueOf(-2).equals(result)) {
            throw new BizException("秒杀库存未预热或活动已结束");
        }
        if (Long.valueOf(-1).equals(result)) {
            throw new BizException("已达限购上限，无法继续购买");
        }
        if (Long.valueOf(0).equals(result)) {
            throw new BizException("手慢了，商品已抢光");
        }
        // 8.3 MQ 削峰：扣减成功即发消息，order 异步建单（快速失败在扣减处已拦截，MQ 只承载成功流量）
        String requestId = "SK" + memberId + "_" + product.getId() + "_" + System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seckillProductId", product.getId());
        body.put("sessionId", product.getSessionId());
        body.put("memberId", memberId);
        body.put("quantity", quantity);
        body.put("requestId", requestId);
        // 收货信息随消息携带（member 服务无地址模块，秒杀页提交时快照）
        body.put("receiverName", dto.getReceiverName());
        body.put("receiverPhone", dto.getReceiverPhone());
        body.put("receiverAddress", dto.getReceiverAddress());
        boolean sent = mqSender.trySend(MqTopics.SECKILL_ORDER, MqTopics.TAG_SUBMIT, body);
        if (!sent) {
            // 消息发送失败：回补 Redis 秒杀库存 + 限购计数，保证不丢单不超卖（对账补偿）
            redisTemplate.opsForValue().increment(stockKey(product.getId()), quantity);
            redisTemplate.opsForValue().increment(limitKey(product.getId(), memberId), -quantity);
            redisTemplate.delete(reservedKey(product.getId(), memberId));
            throw new BizException("系统繁忙，请重新抢购");
        }
        log.info("秒杀提交成功 memberId={} productId={} quantity={} requestId={}", memberId, product.getId(), quantity, requestId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("queued", true);
        row.put("seckillProductId", product.getId());
        row.put("requestId", requestId);
        row.put("tip", "抢购成功，正在为您下单，请稍候查询结果");
        return row;
    }

    /** 发放秒杀幂等 token（12.3）：同用户只保留最新一个 */
    public String issueToken(Long memberId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(tokenKey(memberId), token, Duration.ofMinutes(10));
        return token;
    }

    // ==================== 结果查询（14.6） ====================

    /** 下单结果轮询：1 成功（返回订单号）2 失败（返回原因），无记录 = 处理中 */
    public Map<String, Object> queryResult(Long memberId, Long seckillProductId) {
        String key = resultKey(memberId, seckillProductId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", 0);
            row.put("tip", "下单处理中，请稍候");
            return row;
        }
        try {
            Map<String, Object> row = objectMapper.readValue(json, Map.class);
            return row;
        } catch (Exception e) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", 0);
            row.put("tip", "下单处理中，请稍候");
            return row;
        }
    }

    /** 写下单结果（order 侧 MQ 消费后回写） */
    public void writeResult(Long memberId, Long seckillProductId, int status, String orderSn, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        if (orderSn != null) {
            row.put("orderSn", orderSn);
        }
        if (reason != null) {
            row.put("reason", reason);
        }
        redisTemplate.opsForValue().set(resultKey(memberId, seckillProductId), toJson(row),
                Duration.ofMinutes(SeckillConstants.RESULT_TTL_MINUTES));
    }

    // ==================== 预扣资格核验 / 关单回补（Dubbo + Feign 双契约） ====================

    /**
     * 核验 Redis 预扣资格（order 落单前调用，防绕过秒杀入口直接下单）：
     * 场次进行中 + reserved 标记存在 + 返回秒杀快照；失败原因返回不抛异常（order 侧按 ok 分支处理）
     */
    public SeckillVerifyResult verifyReservation(Long seckillProductId, Long memberId, Integer quantity) {
        SeckillVerifyResult result = new SeckillVerifyResult();
        result.setSeckillProductId(seckillProductId);
        result.setMemberId(memberId);
        result.setQuantity(quantity);
        try {
            SeckillProduct product = productService.getById(seckillProductId);
            SeckillSession session = sessionService.getById(product.getSessionId());
            if (!sessionService.isOngoing(session)) {
                result.setReason("秒杀活动已结束");
                return result;
            }
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(reservedKey(seckillProductId, memberId)))) {
                result.setReason("无秒杀预扣资格，请从秒杀入口抢购");
                return result;
            }
            SkuOrderInfoDTO sku = productFeignClient.getSkuOrderInfo(product.getSkuId()).getDataOrThrow();
            result.setOk(true);
            result.setSessionId(product.getSessionId());
            result.setSpuId(product.getSpuId());
            result.setSkuId(product.getSkuId());
            result.setSkuCode(sku.getSkuCode());
            result.setSeckillPrice(product.getSeckillPrice());
            result.setSpuName(sku.getSpuName());
            result.setSpec(sku.getSpec());
            result.setPic(sku.getPic());
        } catch (BizException e) {
            result.setReason(e.getMessage());
        } catch (Exception e) {
            log.error("核验秒杀预扣资格异常 productId={} memberId={}", seckillProductId, memberId, e);
            result.setReason("系统繁忙，请稍后重试");
        }
        return result;
    }

    /**
     * 秒杀订单关单回补（order 超时关单 0→4 后调用，幂等）：
     * 活动进行中 → 回补 Redis 秒杀库存 + 清预扣资格 + 删结果（该订单已取消，结果作废）；
     * 活动已结束 → 回补 product_sku.stock（change_type=9 秒杀回补）
     */
    public void releaseSeckillStock(String orderSn, Long seckillProductId, Long skuId, Integer quantity, Long memberId) {
        String releasedKey = SeckillConstants.KEY_RELEASED + orderSn;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(releasedKey, "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(first)) {
            return; // 已回补（幂等）
        }
        try {
            SeckillProduct product = productService.getById(seckillProductId);
            SeckillSession session = sessionService.getById(product.getSessionId());
            if (sessionService.isOngoing(session)) {
                // 活动进行中：回补 Redis 秒杀库存（用户可继续抢）
                redisTemplate.opsForValue().increment(stockKey(seckillProductId), quantity);
                redisTemplate.delete(reservedKey(seckillProductId, memberId));
                redisTemplate.delete(resultKey(memberId, seckillProductId));
                log.info("秒杀订单关单回补 Redis 库存 orderSn={} productId={} quantity={}", orderSn, seckillProductId, quantity);
            } else {
                // 活动已结束：回补 sku.stock（change_type=9，product 侧幂等）；
                // 先查是否真实扣过（change_type=4 流水），防"落单后未扣减"场景回补虚增
                boolean deducted = productFeignClient.hasSeckillDeducted(orderSn, skuId).getDataOrThrow();
                if (deducted) {
                    ReleaseStockDTO dto = new ReleaseStockDTO();
                    dto.setBizSn(orderSn);
                    dto.setSkuId(skuId);
                    dto.setQuantity(quantity);
                    dto.setChangeType(9);
                    productFeignClient.releaseStock(dto).getDataOrThrow();
                    log.info("秒杀订单关单回补 sku 库存 orderSn={} skuId={} quantity={}", orderSn, skuId, quantity);
                } else {
                    log.info("秒杀订单未扣过 sku 库存，跳过回补 orderSn={} skuId={}", orderSn, skuId);
                }
            }
        } catch (Exception e) {
            redisTemplate.delete(releasedKey);
            log.error("秒杀订单关单回补失败 orderSn={}", orderSn, e);
            throw new BizException("秒杀回补失败，请稍后重试");
        }
    }

    // ==================== 内部 ====================

    public String sessionKey(Long sessionId) {
        return SeckillConstants.KEY_SESSION + sessionId;
    }

    public String productsKey(Long sessionId) {
        return SeckillConstants.KEY_PRODUCTS + sessionId;
    }

    public String stockKey(Long seckillProductId) {
        return SeckillConstants.KEY_STOCK + seckillProductId;
    }

    /** 限购计数 key：{pid}:{memberId} → 已购数量（每人限购，活动期间有效，预热时通配清理） */
    public String limitKey(Long seckillProductId, Long memberId) {
        return SeckillConstants.KEY_LIMIT + seckillProductId + ":" + memberId;
    }

    /** 预扣资格 key：{pid}:{memberId}（Lua 扣减成功写入，order 落单前核验，防止绕过秒杀入口） */
    public String reservedKey(Long seckillProductId, Long memberId) {
        return SeckillConstants.KEY_RESERVED + seckillProductId + ":" + memberId;
    }

    /** SCAN 分批删除匹配 pattern 的 key（预热清理限购/预扣残留，避免 KEYS 阻塞） */
    private void scanDelete(String pattern) {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            List<String> keys = new ArrayList<>();
            cursor.forEachRemaining(keys::add);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    public String resultKey(Long memberId, Long seckillProductId) {
        return SeckillConstants.KEY_RESULT + memberId + ":" + seckillProductId;
    }

    public String tokenKey(Long memberId) {
        return SeckillConstants.KEY_TOKEN + memberId;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("数据序列化失败");
        }
    }
}
