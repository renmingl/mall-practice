package com.mall.portal.service;

import com.mall.api.cart.CartFeignClient;
import com.mall.api.cart.CartItemDTO;
import com.mall.api.coupon.CouponAvailableDTO;
import com.mall.api.coupon.CouponFeignClient;
import com.mall.api.product.ProductFeignClient;
import com.mall.api.product.SkuOrderInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 结算预览聚合（阶段 4）：购物车勾选项 + 商品实时信息（价格/库存/上下架）+ 可用优惠券
 * 仅聚合展示不下单：金额与优惠以 order 下单时的实时校验为准（本页数据仅供确认页展示）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CartFeignClient cartFeignClient;
    private final ProductFeignClient productFeignClient;
    private final CouponFeignClient couponFeignClient;

    /** 结算预览：勾选商品行（含状态标记）+ 商品总额 + 可用优惠券 */
    public Map<String, Object> preview(Long memberId) {
        List<CartItemDTO> checkedItems = cartFeignClient.getCheckedItems(memberId).getDataOrThrow();
        List<Long> skuIds = checkedItems.stream().map(CartItemDTO::getSkuId).toList();
        List<SkuOrderInfoDTO> skuInfos = skuIds.isEmpty()
                ? List.of()
                : productFeignClient.getSkuOrderInfos(skuIds).getDataOrThrow();
        Map<Long, SkuOrderInfoDTO> skuMap = skuInfos.stream()
                .collect(Collectors.toMap(SkuOrderInfoDTO::getSkuId, Function.identity()));

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemDTO item : checkedItems) {
            SkuOrderInfoDTO sku = skuMap.get(item.getSkuId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", item.getSkuId());
            row.put("quantity", item.getQuantity());
            if (sku == null) {
                row.put("invalid", true);          // 商品已删除/无快照
                items.add(row);
                continue;
            }
            row.put("spuId", sku.getSpuId());
            row.put("spuName", sku.getSpuName());
            row.put("spec", sku.getSpec());
            row.put("pic", sku.getPic());
            row.put("price", sku.getPrice());
            row.put("stock", sku.getStock());
            row.put("status", sku.getStatus());
            row.put("spuStatus", sku.getSpuStatus());
            // 仅有效商品计入总额（下架/停用/无库存行由前端提示，下单时 order 侧强校验）
            boolean valid = sku.getStatus() == 1 && sku.getSpuStatus() == 1
                    && sku.getStock() != null && sku.getStock() >= item.getQuantity();
            row.put("valid", valid);
            if (valid) {
                totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            items.add(row);
        }
        List<CouponAvailableDTO> coupons = totalAmount.compareTo(BigDecimal.ZERO) > 0
                ? couponFeignClient.getAvailableCoupons(memberId, totalAmount).getDataOrThrow()
                : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalAmount", totalAmount);
        result.put("availableCoupons", coupons);
        log.info("结算预览 memberId={} items={} totalAmount={}", memberId, items.size(), totalAmount);
        return result;
    }
}
