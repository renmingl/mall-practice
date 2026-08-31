package com.mall.cart.controller;

import com.mall.cart.service.CartService;
import com.mall.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 前台购物车（阶段 4）：Redis Hash 存储，经网关鉴权后透传 X-User-Id
 * 接口：列表/加购/改数量/勾选/删除/角标计数
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 购物车列表（合并商品快照，失效商品 invalid=true 前端置灰） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestHeader("X-User-Id") Long memberId) {
        return Result.success(cartService.list(memberId));
    }

    /** 加购 */
    @PostMapping("/add")
    public Result<Void> add(@RequestHeader("X-User-Id") Long memberId,
                            @RequestParam Long skuId,
                            @RequestParam(defaultValue = "1") int quantity) {
        cartService.add(memberId, skuId, quantity);
        return Result.success();
    }

    /** 修改数量 */
    @PostMapping("/update")
    public Result<Void> update(@RequestHeader("X-User-Id") Long memberId,
                               @RequestParam Long skuId,
                               @RequestParam int quantity) {
        cartService.updateQuantity(memberId, skuId, quantity);
        return Result.success();
    }

    /** 批量勾选/取消勾选 */
    @PostMapping("/check")
    public Result<Void> check(@RequestHeader("X-User-Id") Long memberId,
                              @RequestBody List<Long> skuIds,
                              @RequestParam boolean checked) {
        cartService.check(memberId, skuIds, checked);
        return Result.success();
    }

    /** 删除条目 */
    @DeleteMapping("/remove")
    public Result<Void> remove(@RequestHeader("X-User-Id") Long memberId,
                               @RequestParam List<Long> skuIds) {
        cartService.remove(memberId, skuIds);
        return Result.success();
    }

    /** 购物车角标：件数合计 */
    @GetMapping("/count")
    public Result<Integer> count(@RequestHeader("X-User-Id") Long memberId) {
        return Result.success(cartService.count(memberId));
    }
}
