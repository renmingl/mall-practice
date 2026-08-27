package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductPurchase;
import com.mall.product.dto.PurchaseCreateDTO;
import com.mall.product.dto.PurchaseReceiveDTO;
import com.mall.product.service.PurchaseService;
import lombok.RequiredArgsConstructor;
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
 * 后台采购管理（场景 15.2/15.3）：创建/列表/详情/审核/取消/分批入库
 * @author renmingl
 * @date 2026-08-27 10:31:30
 */
@RestController
@RequestMapping("/api/admin/purchase")
@RequiredArgsConstructor
public class AdminPurchaseController {

    private final PurchaseService purchaseService;

    /** 创建采购单（0 待审核） */
    @PostMapping
    public Result<Long> create(@RequestBody PurchaseCreateDTO dto) {
        return Result.success(purchaseService.create(dto));
    }

    @GetMapping("/list")
    public Result<Page<ProductPurchase>> page(@RequestParam(required = false) Byte status,
                                              @RequestParam(required = false) Long supplierId,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(purchaseService.page(status, supplierId, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(purchaseService.detail(id));
    }

    /** 审核：pass=true 通过 → 待收货；false 驳回 → 已取消 */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam Boolean pass) {
        purchaseService.audit(id, pass, "admin");
        return Result.success();
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        purchaseService.cancel(id);
        return Result.success();
    }

    /** 分批入库（明细级收货，收满自动置已完成） */
    @PostMapping("/receive")
    public Result<Void> receive(@RequestBody PurchaseReceiveDTO dto) {
        purchaseService.receive(dto);
        return Result.success();
    }
}
