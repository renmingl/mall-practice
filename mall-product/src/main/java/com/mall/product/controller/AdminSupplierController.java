package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductSupplier;
import com.mall.product.service.SupplierService;
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
 * 后台供应商管理（场景 15.1）：档案 CRUD / 停用
 * @author renmingl
 * @date 2026-08-27 10:31:25
 */
@RestController
@RequestMapping("/api/admin/supplier")
@RequiredArgsConstructor
public class AdminSupplierController {

    private final SupplierService supplierService;

    @GetMapping("/list")
    public Result<Page<ProductSupplier>> page(@RequestParam(required = false) String name,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(supplierService.page(name, status, page, size));
    }

    @PostMapping
    public Result<Void> add(@RequestBody ProductSupplier supplier) {
        supplierService.add(supplier);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody ProductSupplier supplier) {
        supplierService.update(supplier);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        supplierService.updateStatus(id, status);
        return Result.success();
    }
}
