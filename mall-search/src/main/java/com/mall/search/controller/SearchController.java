package com.mall.search.controller;

import com.mall.common.result.Result;
import com.mall.search.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 搜索前台接口（阶段 8 13.2）：商品全文检索（高亮）/ 搜索联想
 * 路由 /api/search/**（网关转发 mall-search，无需登录）
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchService searchService;

    /** 商品搜索：keyword 非空走全文检索（高亮 name/subtitle），空走销量榜兜底；categoryId 可选过滤 */
    @GetMapping
    public Result<Map<String, Object>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "categoryId", required = false) Long categoryId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return Result.success(searchService.search(keyword, categoryId, page, size));
    }

    /** 搜索联想：输入前缀实时返回商品名称候选（前 8 条） */
    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam("prefix") String prefix) {
        return Result.success(searchService.suggest(prefix));
    }
}
