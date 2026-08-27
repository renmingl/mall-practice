package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductSpu;
import com.mall.product.dto.SpuSaveDTO;
import com.mall.product.service.ProductService;
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

import java.util.Map;

/**
 * 后台商品管理（场景 2.2/2.3）：列表/详情/保存/上下架/删除/缓存预热
 * @author renmingl
 * @date 2026-08-27 10:31:15
 */
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping("/list")
    public Result<Page<ProductSpu>> page(@RequestParam(required = false) String spuCode,
                                         @RequestParam(required = false) String name,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.adminPage(spuCode, name, categoryId, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(productService.adminDetail(id));
    }

    @PostMapping
    public Result<Void> save(@RequestBody SpuSaveDTO dto) {
        productService.save(dto);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    /** 缓存预热手动触发（场景 2.5；xxl-job 接入前使用） */
    @PostMapping("/preload")
    public Result<Integer> preload() {
        return Result.success(productService.preload());
    }
}
