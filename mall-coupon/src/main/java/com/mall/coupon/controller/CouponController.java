package com.mall.coupon.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台优惠券（阶段 4）：领券中心/领券/我的优惠券
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 领券中心列表（进行中 + 未过期 + 未领完；附每人剩余可领数） */
    @GetMapping("/center")
    public Result<Page<Map<String, Object>>> center(@RequestHeader("X-User-Id") Long memberId,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.success(couponService.portalPage(memberId, page, size));
    }

    /** 领券（SETNX 幂等 + 条件更新防超领） */
    @PostMapping("/receive")
    public Result<Void> receive(@RequestHeader("X-User-Id") Long memberId,
                                @RequestParam Long couponId) {
        couponService.receive(memberId, couponId);
        return Result.success();
    }

    /** 我的优惠券（status 筛选：0未使用 1已锁定 2已使用 3已过期） */
    @GetMapping("/mine")
    public Result<Page<Map<String, Object>>> mine(@RequestHeader("X-User-Id") Long memberId,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(couponService.mine(memberId, status, page, size));
    }
}
