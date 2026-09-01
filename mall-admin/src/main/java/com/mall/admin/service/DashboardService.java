package com.mall.admin.service;

import com.mall.api.member.MemberFeignClient;
import com.mall.api.order.OrderFeignClient;
import com.mall.api.product.ProductFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板聚合（场景 10.1～10.6）：订单今日概览 / 7 天趋势（order 侧 DB 统计）、
 * 会员在线 / 日活 / 签到 / 新增（member 侧 Redis Bitmap/ZSET + DB）、
 * 商品 PV/UV / 浏览排行 / 销量榜 / 库存预警（product 侧 Redis + DB）
 * 各统计口径由归属服务实现（数据在哪个服务就由哪个服务统计），本服务只做一次 Feign 聚合
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderFeignClient orderFeignClient;
    private final MemberFeignClient memberFeignClient;
    private final ProductFeignClient productFeignClient;

    /** 看板聚合：今日概览 + 会员运营 + 7 天趋势 + 销量/浏览榜 + 库存预警 */
    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("today", orderFeignClient.todayStats().getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取订单统计失败", e);
            data.put("today", Map.of());
        }
        try {
            data.put("member", memberFeignClient.statsSummary().getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取会员统计失败", e);
            data.put("member", Map.of());
        }
        try {
            data.put("trend7d", orderFeignClient.trend7d().getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取订单趋势失败", e);
            data.put("trend7d", List.of());
        }
        try {
            data.put("salesRank", productFeignClient.salesRank(10).getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取销量榜失败", e);
            data.put("salesRank", List.of());
        }
        try {
            data.put("viewsRank", productFeignClient.topViews(10).getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取浏览榜失败", e);
            data.put("viewsRank", List.of());
        }
        try {
            data.put("warnings", productFeignClient.stockWarnings().getDataOrThrow());
        } catch (Exception e) {
            log.warn("看板拉取库存预警失败", e);
            data.put("warnings", List.of());
        }
        return data;
    }
}
