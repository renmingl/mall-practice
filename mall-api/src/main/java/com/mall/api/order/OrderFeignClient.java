package com.mall.api.order;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 订单服务内部契约（product 评价校验、payment 支付状态回写/退款校验调用）
 * 状态回写均为条件更新（幂等）：仅当订单处于目标前置状态时才流转，重复调用不产生副作用
 * 运营数据（10.4）：今日订单概览 / 近 7 天订单趋势（admin 看板聚合）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@FeignClient(name = "mall-order", path = "/internal/order", contextId = "orderFeignClient")
public interface OrderFeignClient {

    /** 查询订单信息（payment 创建支付单/申请退款校验用） */
    @GetMapping("/info")
    Result<OrderInfoDTO> getOrderInfo(@RequestParam("orderSn") String orderSn);

    /** 查询订单项明细（payment 退款联动时取明细，随消息投递给 product 回补库存） */
    @GetMapping("/items")
    Result<List<OrderItemInfoDTO>> getOrderItems(@RequestParam("orderSn") String orderSn);

    /** 评价前校验订单项（存在性 + 归属会员 + 订单已完成 + 未评价） */
    @GetMapping("/comment-validate")
    Result<CommentValidateResult> validateCommentable(@RequestParam("orderItemId") Long orderItemId,
                                                      @RequestParam("memberId") Long memberId);

    /** 支付成功回写：0待付款→1待发货，记录支付时间（payment 回调成功后同步调用，幂等） */
    @PostMapping("/mark-paid")
    Result<Void> markPaid(@RequestParam("orderSn") String orderSn,
                          @RequestParam("payType") Byte payType,
                          @RequestParam("payTime") String payTime);

    /** 整单退款成功回写：1/2/3→5已退款（payment 退款成功后调用，幂等） */
    @PostMapping("/mark-refunded")
    Result<Void> markRefunded(@RequestParam("orderSn") String orderSn);

    /** 今日订单概览（看板：今日订单数 / 已支付销售额 / 秒杀订单数） */
    @GetMapping("/stats/today")
    Result<Map<String, Object>> todayStats();

    /** 近 7 天订单趋势（看板：每天订单数 + 已支付销售额） */
    @GetMapping("/stats/trend")
    Result<List<Map<String, Object>>> trend7d();

    /** 最近订单（阶段 9 16.3 AI 问答供给：按会员查最近 N 单精简摘要，含商品项） */
    @GetMapping("/recent")
    Result<List<Map<String, Object>>> recentOrders(@RequestParam("memberId") Long memberId,
                                                   @RequestParam(value = "limit", defaultValue = "5") Integer limit);
}
