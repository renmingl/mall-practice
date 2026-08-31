package com.mall.api.cart;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 购物车服务内部契约（portal 结算预览、order 下单取勾选条目调用）
 * 购物车为纯 Redis 存储（Hash：key=cart:{memberId}，field=skuId），无数据库依赖
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@FeignClient(name = "mall-cart", path = "/internal/cart", contextId = "cartFeignClient")
public interface CartFeignClient {

    /** 勾选条目（结算/下单用；只返回 checked=true 的条目） */
    @GetMapping("/checked-items/{memberId}")
    Result<List<CartItemDTO>> getCheckedItems(@PathVariable("memberId") Long memberId);

    /** 下单成功后清理已结算的勾选条目 */
    @DeleteMapping("/checked/{memberId}")
    Result<Void> removeChecked(@PathVariable("memberId") Long memberId, @RequestParam("skuIds") List<Long> skuIds);
}
