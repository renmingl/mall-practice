package com.mall.search.controller;

import com.mall.common.result.Result;
import com.mall.search.service.ProductIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 搜索管理接口（阶段 8 13.2）：reindex 全量重建 / 索引信息
 * 路由 /api/admin/search/**（网关转发 mall-search；reindex 幂等可重复执行，Canal 增量未就绪时手动兜底）
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private final ProductIndexService indexService;

    /** 全量重建商品索引（DB → ES bulk；返回索引文档数） */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        int count = indexService.reindex();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("index", ProductIndexService.INDEX);
        data.put("docCount", count);
        return Result.success(data);
    }

    /** 索引信息（存在性 + 文档数，ES 不可用时带 error 字段） */
    @GetMapping("/index")
    public Result<Map<String, Object>> indexInfo() {
        return Result.success(indexService.indexInfo());
    }
}
