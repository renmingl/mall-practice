package com.mall.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import com.mall.product.util.ProductJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台 SKU 搜索（阶段 7 秒杀商品配置选品）：按 SKU 编码 / SPU 编码 / 商品名模糊搜索
 * @author renmingl
 * @date 2026-09-01 10:30:00
 */
@RestController
@RequestMapping("/api/admin/sku")
@RequiredArgsConstructor
public class AdminSkuController {

    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;

    /** SKU 远程搜索（keyword 非空，最多 20 条；供秒杀配置弹窗远程搜索选择） */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam("keyword") String keyword) {
        // 先按 spu 编码/名称反查 spuId 列表，避免 SQL 拼接注入
        List<Long> spuIds = spuMapper.selectList(new LambdaQueryWrapper<ProductSpu>()
                        .like(ProductSpu::getSpuCode, keyword)
                        .or()
                        .like(ProductSpu::getName, keyword))
                .stream().map(ProductSpu::getId).toList();
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .like(ProductSku::getSkuCode, keyword)
                .or(!spuIds.isEmpty(), w -> w.in(ProductSku::getSpuId, spuIds))
                .last("LIMIT 20"));
        Map<Long, String> spuNames = new HashMap<>();
        List<Long> spuIdList = skus.stream().map(ProductSku::getSpuId).distinct().toList();
        if (!spuIdList.isEmpty()) {
            for (ProductSpu spu : spuMapper.selectBatchIds(spuIdList)) {
                spuNames.put(spu.getId(), spu.getName());
            }
        }
        List<Map<String, Object>> result = skus.stream().map(sku -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sku.getId());
            row.put("skuCode", sku.getSkuCode());
            row.put("spuId", sku.getSpuId());
            row.put("spuName", spuNames.getOrDefault(sku.getSpuId(), ""));
            row.put("spec", ProductJsonUtil.unwrapText(sku.getSpec()));
            row.put("price", sku.getPrice());
            row.put("stock", sku.getStock());
            row.put("status", sku.getStatus());
            return row;
        }).toList();
        return Result.success(result);
    }
}
