package com.mall.order.controller;

import com.mall.api.order.CommentValidateResult;
import com.mall.api.order.OrderInfoDTO;
import com.mall.api.order.OrderItemInfoDTO;
import com.mall.common.result.Result;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
