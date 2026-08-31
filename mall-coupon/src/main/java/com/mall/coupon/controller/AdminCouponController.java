package com.mall.coupon.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.coupon.dto.CouponSaveDTO;
import com.mall.coupon.service.CouponService;
import com.mall.mbg.entity.Coupon;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台优惠券模板管理：分页/新增修改/上架结束（阶段 4 营销）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;

    /** 模板分页（支持名称/状态筛选） */
    @GetMapping("/page")
    public Result<Page<Coupon>> page(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size) {
        return Result.success(couponService.adminPage(name, status, page, size));
    }

    /** 新增/修改模板 */
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody CouponSaveDTO dto) {
        couponService.save(dto);
        return Result.success();
    }

    /** 模板状态：1进行中 0已结束 */
    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        couponService.updateStatus(id, status);
        return Result.success();
    }
}
