package com.mall.seckill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.seckill.dto.SessionSaveDTO;
import com.mall.seckill.service.SeckillProductService;
import com.mall.seckill.service.SeckillService;
import com.mall.seckill.service.SeckillSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 秒杀后台管理（14.1 / 14.2 / 14.3）：场次 CRUD/启停、秒杀商品配置、手动预热
 * 路由 /api/admin/seckill/**（网关已放行后台鉴权）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillSessionService sessionService;
    private final SeckillProductService productService;
    private final SeckillService seckillService;

    // ==================== 场次管理（14.1） ====================

    /** 场次分页（keyword 名称 / status 状态筛选） */
    @GetMapping("/session/page")
    public Result<Page<Map<String, Object>>> sessionPage(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.success(sessionService.adminPage(keyword, status, page, size));
    }

    /** 保存场次（新增/修改） */
    @PostMapping("/session/save")
    public Result<Void> sessionSave(@Valid @RequestBody SessionSaveDTO dto) {
        sessionService.save(dto);
        return Result.success();
    }

    /** 启停场次（0禁用 1启用） */
    @PostMapping("/session/{id}/toggle")
    public Result<Void> sessionToggle(@PathVariable Long id, @RequestParam("status") Byte status) {
        sessionService.toggle(id, status);
        return Result.success();
    }

    // ==================== 秒杀商品配置（14.2） ====================

    /** 秒杀商品分页（按场次/状态筛选） */
    @GetMapping("/product/page")
    public Result<Page<Map<String, Object>>> productPage(@RequestParam(required = false) Long sessionId,
                                                         @RequestParam(required = false) Integer status,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.adminPage(sessionId, status, page, size));
    }

    /** 场次下启用商品列表（配置页下拉/展示） */
    @GetMapping("/product/list")
    public Result<List<Map<String, Object>>> productList(@RequestParam("sessionId") Long sessionId) {
        return Result.success(productService.listBySession(sessionId));
    }

    /** 保存秒杀商品配置（新增/修改） */
    @PostMapping("/product/save")
    public Result<Void> productSave(@Valid @RequestBody com.mall.seckill.dto.SeckillProductSaveDTO dto) {
        productService.save(dto);
        return Result.success();
    }

    /** 启停秒杀商品（0禁用 1启用） */
    @PostMapping("/product/{id}/toggle")
    public Result<Void> productToggle(@PathVariable Long id, @RequestParam("status") Byte status) {
        productService.toggle(id, status);
        return Result.success();
    }

    /** 删除秒杀商品配置（场次未开始才允许） */
    @PostMapping("/product/{id}/delete")
    public Result<Void> productDelete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    // ==================== 库存预热（14.3） ====================

    /** 手动预热场次（覆盖式，可重复执行；校验 seckill_stock ≤ sku.stock） */
    @PostMapping("/session/{sessionId}/preheat")
    public Result<Void> preheat(@PathVariable Long sessionId) {
        seckillService.preheat(sessionId);
        return Result.success();
    }
}
