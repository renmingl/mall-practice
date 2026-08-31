package com.mall.payment.controller;

import com.mall.api.payment.CreatePaymentDTO;
import com.mall.api.payment.PaymentDTO;
import com.mall.common.result.Result;
import com.mall.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付服务内部接口（实现 mall-api PaymentFeignClient 契约，仅服务间调用，网关不暴露）
 * order 拉起收银台创建支付单；portal 支付结果页查单
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/internal/payment")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentService paymentService;

    /** 创建支付单（幂等：同订单+同支付方式复用已存在流水） */
    @PostMapping("/create")
    public Result<PaymentDTO> create(@RequestBody CreatePaymentDTO dto) {
        return Result.success(paymentService.createPayment(dto));
    }

    /** 按订单号查支付流水（支付结果页轮询用，不校验会员归属） */
    @GetMapping("/by-order")
    public Result<PaymentDTO> byOrder(@RequestParam("orderSn") String orderSn) {
        return Result.success(paymentService.getByOrder(orderSn));
    }
}
