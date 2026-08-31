package com.mall.cart.controller;

import com.mall.api.cart.CartItemDTO;
import com.mall.cart.service.CartService;
import com.mall.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车服务内部接口（实现 mall-api CartFeignClient 契约，仅服务间调用，网关不暴露）
 * order 下单取勾选条目、下单成功后清理已结算条目
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/internal/cart")
@RequiredArgsConstructor
public class CartInternalController {

    private final CartService cartService;

    /** 勾选条目（结算/下单用；失效商品也返回，由下游校验拦截） */
    @GetMapping("/checked-items/{memberId}")
    public Result<List<CartItemDTO>> getCheckedItems(@PathVariable("memberId") Long memberId) {
        return Result.success(cartService.getCheckedItems(memberId));
    }

    /** 下单成功后清理已结算的勾选条目 */
    @DeleteMapping("/checked/{memberId}")
    public Result<Void> removeChecked(@PathVariable("memberId") Long memberId,
                                      @RequestParam("skuIds") List<Long> skuIds) {
        cartService.removeChecked(memberId, skuIds);
        return Result.success();
    }
}
