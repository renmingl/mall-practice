package com.mall.cart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.api.cart.CartItemDTO;
import com.mall.api.product.ProductFeignClient;
import com.mall.api.product.SkuOrderInfoDTO;
import com.mall.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 购物车服务：纯 Redis Hash 存储（key=cart:{memberId}，field=skuId，value=JSON {quantity, checked}）
 * 无数据库依赖；列表组装调 product 取 SKU 快照（价格/上下架/库存），失效商品标记 invalid 供前端置灰
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final int MAX_QUANTITY = 99;

    private final StringRedisTemplate redisTemplate;
    private final ProductFeignClient productFeignClient;
    private final ObjectMapper objectMapper;

    /** 加购：已存在则累加（上限 99），不存在则新增（默认勾选） */
    public void add(Long memberId, Long skuId, int quantity) {
        validateQuantity(quantity);
        String key = cartKey(memberId);
        Object raw = redisTemplate.opsForHash().get(key, String.valueOf(skuId));
        CartItem item;
        if (raw == null) {
            item = new CartItem(quantity, true);
        } else {
            item = parse(String.valueOf(raw));
            item.setQuantity(Math.min(item.getQuantity() + quantity, MAX_QUANTITY));
        }
        redisTemplate.opsForHash().put(key, String.valueOf(skuId), toJson(item));
    }

    /** 修改数量 */
    public void updateQuantity(Long memberId, Long skuId, int quantity) {
        validateQuantity(quantity);
        String key = cartKey(memberId);
        Object raw = redisTemplate.opsForHash().get(key, String.valueOf(skuId));
        if (raw == null) {
            throw new BizException("购物车中不存在该商品");
        }
        CartItem item = parse(String.valueOf(raw));
        item.setQuantity(quantity);
        redisTemplate.opsForHash().put(key, String.valueOf(skuId), toJson(item));
    }

    /** 批量勾选/取消勾选 */
    public void check(Long memberId, List<Long> skuIds, boolean checked) {
        String key = cartKey(memberId);
        for (Long skuId : skuIds) {
            Object raw = redisTemplate.opsForHash().get(key, String.valueOf(skuId));
            if (raw == null) {
                continue;
            }
            CartItem item = parse(String.valueOf(raw));
            item.setChecked(checked);
            redisTemplate.opsForHash().put(key, String.valueOf(skuId), toJson(item));
        }
    }

    /** 删除条目 */
    public void remove(Long memberId, List<Long> skuIds) {
        String key = cartKey(memberId);
        redisTemplate.opsForHash().delete(key, skuIds.stream().map(String::valueOf).toArray());
    }

    /** 购物车列表：合并 SKU 快照，返回展示数据（含失效标记/小计） */
    public List<Map<String, Object>> list(Long memberId) {
        Map<Object, Object> entries = hash(memberId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = entries.keySet().stream()
                .map(k -> Long.valueOf(String.valueOf(k))).toList();
        Map<Long, SkuOrderInfoDTO> skuMap = productFeignClient.getSkuOrderInfos(skuIds)
                .getDataOrThrow().stream()
                .collect(Collectors.toMap(SkuOrderInfoDTO::getSkuId, s -> s));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long skuId = Long.valueOf(String.valueOf(entry.getKey()));
            CartItem item = parse(String.valueOf(entry.getValue()));
            SkuOrderInfoDTO sku = skuMap.get(skuId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", skuId);
            row.put("quantity", item.getQuantity());
            row.put("checked", item.isChecked());
            if (sku == null) {
                // SKU 已删除：保留条目并标记失效
                row.put("invalid", true);
                row.put("price", BigDecimal.ZERO);
                row.put("subtotal", BigDecimal.ZERO);
            } else {
                boolean invalid = sku.getStatus() == null || sku.getStatus() != 1
                        || sku.getSpuStatus() == null || sku.getSpuStatus() != 1;
                row.put("invalid", invalid);
                row.put("skuCode", sku.getSkuCode());
                row.put("spuId", sku.getSpuId());
                row.put("spuName", sku.getSpuName());
                row.put("spec", sku.getSpec());
                row.put("pic", sku.getPic());
                row.put("price", sku.getPrice());
                row.put("stock", sku.getStock());
                row.put("subtotal", sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            result.add(row);
        }
        return result;
    }

    /** 购物车角标：条目数（件数合计，含未勾选） */
    public int count(Long memberId) {
        return hash(memberId).values().stream()
                .mapToInt(v -> parse(String.valueOf(v)).getQuantity())
                .sum();
    }

    /** 勾选条目（结算/下单用；失效商品也返回，由下游校验拦截） */
    public List<CartItemDTO> getCheckedItems(Long memberId) {
        Map<Object, Object> entries = hash(memberId);
        List<CartItemDTO> items = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            CartItem item = parse(String.valueOf(entry.getValue()));
            if (item.isChecked()) {
                CartItemDTO dto = new CartItemDTO();
                dto.setSkuId(Long.valueOf(String.valueOf(entry.getKey())));
                dto.setQuantity(item.getQuantity());
                dto.setChecked(true);
                items.add(dto);
            }
        }
        return items;
    }

    /** 下单成功后清理已结算的勾选条目 */
    public void removeChecked(Long memberId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        String key = cartKey(memberId);
        redisTemplate.opsForHash().delete(key, skuIds.stream().map(String::valueOf).toArray());
    }

    // ==================== 内部 ====================

    private Map<Object, Object> hash(Long memberId) {
        return redisTemplate.opsForHash().entries(cartKey(memberId));
    }

    private String cartKey(Long memberId) {
        return CART_KEY_PREFIX + memberId;
    }

    private CartItem parse(String json) {
        try {
            return objectMapper.readValue(json, CartItem.class);
        } catch (Exception e) {
            throw new BizException("购物车数据异常，请重新加入");
        }
    }

    private String toJson(CartItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            throw new BizException("购物车数据序列化失败");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new BizException("购买数量需在 1~" + MAX_QUANTITY + " 之间");
        }
    }

    /** 购物车条目值对象（Redis Hash value） */
    private static class CartItem {
        private int quantity;
        private boolean checked;

        public CartItem() {
        }

        public CartItem(int quantity, boolean checked) {
            this.quantity = quantity;
            this.checked = checked;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }
}
