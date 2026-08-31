package com.mall.api.payment;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付服务内部契约（order 拉起收银台创建支付单、portal 支付结果页查询调用）
 * 支付回调/退款由 payment 内部处理；支付成功后通过 MQ 异步通知发积分，订单状态经 OrderFeignClient 回写
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@FeignClient(name = "mall-payment", path = "/internal/payment", contextId = "paymentFeignClient")
public interface PaymentFeignClient {

    /** 创建支付单（幂等：同订单+同支付方式复用已存在流水；返回收银台所需信息） */
    @PostMapping("/create")
    Result<PaymentDTO> createPayment(@RequestBody CreatePaymentDTO dto);

    /** 按订单号查支付流水（收银台/支付结果页轮询用） */
    @GetMapping("/by-order")
    Result<PaymentDTO> getPaymentByOrder(@RequestParam("orderSn") String orderSn);
}
