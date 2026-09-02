package com.mall.order.controller;

import com.mall.api.order.CommentValidateResult;
import com.mall.api.order.OrderInfoDTO;
import com.mall.api.order.OrderItemInfoDTO;
import com.mall.common.result.Result;
import com.mall.mbg.entity.OrderItem;
import com.mall.mbg.entity.Orders;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务内部接口（实现 mall-api OrderFeignClient 契约，仅服务间调用，网关不暴露）
 * product 评价校验、payment 支付状态回写/退款回写
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/internal/order")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderService orderService;

    /** 订单信息（payment 创建支付单/申请退款校验用） */
    @GetMapping("/info")
    public Result<OrderInfoDTO> getOrderInfo(@RequestParam("orderSn") String orderSn) {
        return Result.success(orderService.getOrderInfo(orderSn));
    }

    /** 订单项明细（payment 退款联动组装消息体） */
    @GetMapping("/items")
    public Result<List<OrderItemInfoDTO>> getOrderItems(@RequestParam("orderSn") String orderSn) {
        return Result.success(orderService.getOrderItems(orderSn));
    }

    /** 评价前校验订单项（存在性 + 归属会员 + 订单已完成） */
    @GetMapping("/comment-validate")
    public Result<CommentValidateResult> validateCommentable(@RequestParam("orderItemId") Long orderItemId,
                                                             @RequestParam("memberId") Long memberId) {
        return Result.success(orderService.validateCommentable(orderItemId, memberId));
    }

    /** 支付成功回写：0→1（payment 回调成功后同步调用；幂等） */
    @PostMapping("/mark-paid")
    public Result<Void> markPaid(@RequestParam("orderSn") String orderSn,
                                 @RequestParam("payType") Byte payType,
                                 @RequestParam("payTime") String payTime) {
        orderService.markPaid(orderSn, payType, payTime);
        return Result.success();
    }

    /** 整单退款成功回写：1/2/3→5（payment 退款成功后调用；幂等） */
    @PostMapping("/mark-refunded")
    public Result<Void> markRefunded(@RequestParam("orderSn") String orderSn) {
        orderService.markRefunded(orderSn);
        return Result.success();
    }

    // ==================== 运营数据（10.4，admin 看板聚合） ====================

    /** 今日订单概览：订单数 / 已支付销售额 / 秒杀订单数 */
    @GetMapping("/stats/today")
    public Result<Map<String, Object>> todayStats() {
        return Result.success(orderService.todayStats());
    }

    /** 近 7 天订单趋势：每天订单数 + 已支付销售额 */
    @GetMapping("/stats/trend")
    public Result<List<Map<String, Object>>> trend7d() {
        return Result.success(orderService.trend7d());
    }

    // ==================== AI 问答数据供给（阶段 9 16.3：mall-ai 按需拉取拼上下文） ====================

    /**
     * 最近订单精简摘要（买家问"我的最近订单"等）：按会员拉最近 N 单，只保留模型可读字段，
     * 避免把完整订单实体塞进 AI 上下文浪费 token；status：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款
     */
    @GetMapping("/recent")
    public Result<List<Map<String, Object>>> recentOrders(@RequestParam("memberId") Long memberId,
                                                          @RequestParam(value = "limit", defaultValue = "5") Integer limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        orderService.pageMine(memberId, null, 1, limit).getRecords().forEach(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderSn", ((Orders) row.get("order")).getOrderSn());
            item.put("status", ((Orders) row.get("order")).getStatus());
            item.put("payAmount", ((Orders) row.get("order")).getPayAmount());
            item.put("createTime", ((Orders) row.get("order")).getCreateTime());
            @SuppressWarnings("unchecked")
            List<OrderItem> orderItems = (List<OrderItem>) row.get("items");
            List<Map<String, Object>> items = new ArrayList<>();
            for (OrderItem oi : orderItems) {
                Map<String, Object> oiMap = new LinkedHashMap<>();
                oiMap.put("spuName", oi.getSpuName());
                oiMap.put("spec", oi.getSpec());
                oiMap.put("price", oi.getPrice());
                oiMap.put("quantity", oi.getQuantity());
                items.add(oiMap);
            }
            item.put("items", items);
            rows.add(item);
        });
        return Result.success(rows);
    }
}
