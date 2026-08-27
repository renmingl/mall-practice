package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductStockLog;
import com.mall.product.dto.StockCheckDTO;
import com.mall.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台库存管理（场景 5.1/5.4/15.4/5.5）：实时库存/流水/预警/盘点调整
 * @author renmingl
 * @date 2026-08-27 10:31:20
 */
@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    /** 实时库存分页（附预警标记） */
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(stockService.page(keyword, page, size));
    }

    /** 库存流水分页（可按 SKU 过滤） */
    @GetMapping("/logs")
    public Result<Page<ProductStockLog>> logs(@RequestParam(required = false) Long skuId,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(stockService.logs(skuId, page, size));
    }

    /** 库存预警列表（stock < low_stock） */
    @GetMapping("/warning")
    public Result<List<Map<String, Object>>> warnings() {
        return Result.success(stockService.warnings());
    }

    /** 盘点调整（change_type=7，报损/报溢留痕） */
    @PutMapping("/check")
    public Result<Void> check(@RequestBody StockCheckDTO dto) {
        stockService.check(dto);
        return Result.success();
    }
}
