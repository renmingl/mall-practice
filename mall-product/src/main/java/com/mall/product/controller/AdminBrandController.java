package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductBrand;
import com.mall.product.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台品牌管理（场景 2.1）：分页 CRUD / 启停
 * @author renmingl
 * @date 2026-08-27 10:31:10
 */
@RestController
@RequestMapping("/api/admin/brand")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @GetMapping("/list")
    public Result<Page<ProductBrand>> page(@RequestParam(required = false) String name,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.success(brandService.page(name, status, page, size));
    }

    @PostMapping
    public Result<Void> add(@RequestBody ProductBrand brand) {
        brandService.add(brand);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody ProductBrand brand) {
        brandService.update(brand);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        brandService.updateStatus(id, status);
        return Result.success();
    }
}
