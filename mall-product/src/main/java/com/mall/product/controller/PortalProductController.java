package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductBrand;
import com.mall.mbg.entity.ProductSpu;
import com.mall.product.service.BrandService;
import com.mall.product.service.CategoryService;
import com.mall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前台商品接口（场景 2.3/2.4/2.5）：分类树/品牌下拉/商品列表/详情/热销
 * @author renmingl
 * @date 2026-08-27 10:31:40
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class PortalProductController {

    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductService productService;

    /** 前台分类树（仅启用） */
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        return Result.success(categoryService.enabledTree());
    }

    /** 启用品牌列表（筛选下拉） */
    @GetMapping("/brands")
    public Result<List<ProductBrand>> brands() {
        return Result.success(brandService.enabledList());
    }

    /** 商品列表（仅上架；分类/品牌/关键词筛选） */
    @GetMapping("/list")
    public Result<Page<ProductSpu>> list(@RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long brandId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.portalPage(categoryId, brandId, keyword, page, size));
    }

    /** 商品详情（缓存三防：穿透/击穿/雪崩） */
    @GetMapping("/detail/{spuId}")
    public Result<Map<String, Object>> detail(@PathVariable Long spuId) {
        return Result.success(productService.detail(spuId));
    }

    /** 热销 Top N */
    @GetMapping("/hot")
    public Result<List<ProductSpu>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(productService.hotList(limit));
    }
}
