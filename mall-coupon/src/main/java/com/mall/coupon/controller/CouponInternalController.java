package com.mall.coupon.controller;

import com.mall.api.coupon.CouponAvailableDTO;
import com.mall.common.result.Result;
import com.mall.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券服务内部接口（实现 mall-api CouponFeignClient 契约，仅服务间调用，网关不暴露）
 * order 下单链路：锁券（全局事务内）→ 取消退券/超时关单退券 → 支付成功核销；payment 退款退券
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/internal/coupon")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponService couponService;

    /** 我的可用券（未使用 + 未过期 + 门槛达标；totalAmount 用于计算折扣券可抵金额） */
    @GetMapping("/available")
    public Result<List<CouponAvailableDTO>> getAvailableCoupons(@RequestParam("memberId") Long memberId,
                                                                @RequestParam("totalAmount") BigDecimal totalAmount) {
        return Result.success(couponService.getAvailableCoupons(memberId, totalAmount));
    }

    /** 我的全部未使用券（阶段 9 16.3 AI 问答供给：不分门槛列出未使用 + 未过期券） */
    @GetMapping("/mine")
    public Result<List<CouponAvailableDTO>> mineCoupons(@RequestParam("memberId") Long memberId) {
        return Result.success(couponService.mineCoupons(memberId));
    }

    /** 锁券：0→1（下单占用，写 order_id 便于取消/退款反查；幂等） */
    @PostMapping("/lock")
    public Result<Void> lockCoupon(@RequestParam("couponUserId") Long couponUserId,
                                   @RequestParam("memberId") Long memberId,
                                   @RequestParam("orderId") Long orderId) {
        couponService.lock(couponUserId, memberId, orderId);
        return Result.success();
    }

    /** 退券：1→0（取消订单/超时关单回退，按订单反查；过期置3） */
    @PostMapping("/unlock")
    public Result<Void> unlockCoupon(@RequestParam("orderId") Long orderId,
                                     @RequestParam("memberId") Long memberId) {
        couponService.unlock(orderId, memberId);
        return Result.success();
    }

    /** 核销：1→2（支付成功确认核销，记录核销订单；按订单反查） */
    @PostMapping("/use")
    public Result<Void> useCoupon(@RequestParam("orderId") Long orderId,
                                  @RequestParam("memberId") Long memberId) {
        couponService.use(orderId, memberId);
        return Result.success();
    }

    /** 退款退券：2→0（整单退款成功后退回，按订单反查；过期置3） */
    @PostMapping("/refund")
    public Result<Void> refundCoupon(@RequestParam("orderId") Long orderId,
                                     @RequestParam("memberId") Long memberId) {
        couponService.refund(orderId, memberId);
        return Result.success();
    }
}
