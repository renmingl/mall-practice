package com.mall.api.coupon;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券服务内部契约（portal 结算预览可用券、order 锁券/核销/退回、payment 退款退券调用）
 * 状态机：0未使用 → 1已锁定（下单占用）→ 2已使用；取消/超时关单 1→0；退款退回 2→0（过期置3）
 * 关联方式：锁券时写入 coupon_user.order_id，取消/退款按订单反查退券（orders 表不存券快照）
 * 锁券/核销/退回均为条件更新（兼容 Seata AT 回滚），非原子自增
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@FeignClient(name = "mall-coupon", path = "/internal/coupon", contextId = "couponFeignClient")
public interface CouponFeignClient {

    /** 我的可用券（未使用 + 未过期 + 门槛达标；totalAmount 用于计算折扣券可抵金额） */
    @GetMapping("/available")
    Result<List<CouponAvailableDTO>> getAvailableCoupons(@RequestParam("memberId") Long memberId,
                                                         @RequestParam("totalAmount") BigDecimal totalAmount);

    /** 我的全部未使用券（阶段 9 16.3 AI 问答供给：不分门槛列出未使用 + 未过期券） */
    @GetMapping("/mine")
    Result<List<CouponAvailableDTO>> mineCoupons(@RequestParam("memberId") Long memberId);

    /** 锁券：0→1（下单占用；写入 order_id 便于取消/退款反查；幂等：同订单已锁定则直接成功） */
    @PostMapping("/lock")
    Result<Void> lockCoupon(@RequestParam("couponUserId") Long couponUserId,
                            @RequestParam("memberId") Long memberId,
                            @RequestParam("orderId") Long orderId);

    /** 退券：1→0（取消订单/超时关单回退；按订单反查，过期置3；幂等） */
    @PostMapping("/unlock")
    Result<Void> unlockCoupon(@RequestParam("orderId") Long orderId,
                              @RequestParam("memberId") Long memberId);

    /** 核销：1→2（支付成功确认核销，记录核销订单；按订单反查，幂等） */
    @PostMapping("/use")
    Result<Void> useCoupon(@RequestParam("orderId") Long orderId,
                           @RequestParam("memberId") Long memberId);

    /** 退款退券：2→0（整单退款成功后退回；按订单反查，过期置3；幂等） */
    @PostMapping("/refund")
    Result<Void> refundCoupon(@RequestParam("orderId") Long orderId,
                              @RequestParam("memberId") Long memberId);
}
