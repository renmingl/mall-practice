package com.mall.portal.controller;

import com.mall.common.result.Result;
import com.mall.portal.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 结算预览（阶段 4）：购物车勾选商品 + 金额 + 可用优惠券聚合
 * memberId 从网关透传的 X-User-Id 获取，不信任前端
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    /** 结算预览（订单确认页数据源；下单仍走 order 服务实时校验） */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestHeader("X-User-Id") Long memberId) {
        return Result.success(checkoutService.preview(memberId));
    }
}
