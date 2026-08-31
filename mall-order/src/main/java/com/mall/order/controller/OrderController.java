package com.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.payment.PaymentDTO;
import com.mall.common.result.Result;
import com.mall.mbg.entity.Orders;
import com.mall.order.dto.OrderCreateDTO;
import com.mall.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 买家订单（阶段 5）：下单（幂等）/取消/确认收货/拉起收银台/详情/我的订单
 * memberId 从网关透传的 X-User-Id 获取，不信任前端
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 下单（requestId 幂等，重复提交返回原订单） */
    @PostMapping("/create")
    public Result<Orders> create(@RequestHeader("X-User-Id") Long memberId,
                                 @Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(memberId, dto));
    }

    /** 取消订单（仅待付款） */
    @PostMapping("/{orderSn}/cancel")
    public Result<Void> cancel(@RequestHeader("X-User-Id") Long memberId,
                               @PathVariable String orderSn) {
        orderService.cancel(memberId, orderSn);
        return Result.success();
    }

    /** 确认收货（仅待收货） */
    @PostMapping("/{orderSn}/confirm-receive")
    public Result<Void> confirmReceive(@RequestHeader("X-User-Id") Long memberId,
                                       @PathVariable String orderSn) {
        orderService.confirmReceive(memberId, orderSn);
        return Result.success();
    }

    /** 拉起收银台：创建支付流水（1支付宝 2微信），返回跳转收银台所需信息 */
    @PostMapping("/{orderSn}/pay")
    public Result<PaymentDTO> pay(@RequestHeader("X-User-Id") Long memberId,
                                  @PathVariable String orderSn,
                                  @RequestParam("payType") Byte payType) {
        return Result.success(orderService.pay(memberId, orderSn, payType));
    }

    /** 订单详情（订单头 + 明细 + 状态流水） */
    @GetMapping("/{orderSn}")
    public Result<Map<String, Object>> detail(@RequestHeader("X-User-Id") Long memberId,
                                              @PathVariable String orderSn) {
        return Result.success(orderService.detail(memberId, orderSn));
    }

    /** 我的订单分页（status 0待付款 1待发货 2待收货 3已完成 4已取消 5已退款） */
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(@RequestHeader("X-User-Id") Long memberId,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.pageMine(memberId, status, page, size));
    }
}
