package com.mall.product.controller;

import com.mall.common.result.Result;
import com.mall.product.service.ProductStatsService;
import com.mall.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前台商品运营接口（10.2 / 10.5 / 10.6）：浏览埋点 / 点赞 / 浏览足迹
 * 详情页打开时前端调用 view 埋点（独立 POST 接口，避免 GET 缓存跳过埋点）；足迹记录同一埋点完成
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/api/product/stats")
@RequiredArgsConstructor
public class PortalProductStatsController {

    /** 网关鉴权后透传的当前用户 ID 请求头（未登录为空，UV 按匿名 ID 去重） */
    public static final String HEADER_USER_ID = "X-User-Id";

    private final ProductStatsService productStatsService;
    private final StockService stockService;

    /** 商品销量排行榜 Top N（10.4：ZSET rank:sales 倒序，公开读接口） */
    @GetMapping("/sales-rank")
    public Result<List<Map<String, Object>>> salesRank(@RequestParam(value = "topN", defaultValue = "10") int topN) {
        return Result.success(stockService.salesRank(Math.max(1, Math.min(topN, 50))));
    }

    /** 浏览埋点（PV + UV + 浏览排行；登录用户同时记录足迹） */
    @PostMapping("/view")
    public Result<Void> trackView(@RequestParam("spuId") Long spuId,
                                  @RequestHeader(value = HEADER_USER_ID, required = false) Long memberId) {
        productStatsService.trackView(spuId, memberId == null ? null : String.valueOf(memberId));
        if (memberId != null) {
            productStatsService.recordHistory(memberId, spuId);
        }
        return Result.success();
    }

    /** 点赞（重复点赞幂等） */
    @PostMapping("/like/{spuId}")
    public Result<Boolean> like(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        return Result.success(productStatsService.like(spuId, memberId));
    }

    /** 取消点赞 */
    @DeleteMapping("/like/{spuId}")
    public Result<Void> unlike(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        productStatsService.unlike(spuId, memberId);
        return Result.success();
    }

    /** 点赞数 */
    @GetMapping("/like/count/{spuId}")
    public Result<Long> likeCount(@PathVariable Long spuId) {
        return Result.success(productStatsService.likeCount(spuId));
    }

    /** 是否已点赞 */
    @GetMapping("/like/status/{spuId}")
    public Result<Boolean> liked(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        return Result.success(productStatsService.liked(spuId, memberId));
    }

    /** 最近浏览足迹（按时间倒序，最多 50 条） */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestHeader(HEADER_USER_ID) Long memberId) {
        return Result.success(productStatsService.history(memberId));
    }
}
