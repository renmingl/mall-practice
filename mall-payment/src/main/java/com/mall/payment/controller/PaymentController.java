package com.mall.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.payment.PaymentDTO;
import com.mall.common.result.Result;
import com.mall.payment.dto.RefundApplyDTO;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.RefundService;
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
 * 买家支付（阶段 6）：模拟支付回调 / 支付结果查询 / 申请退款 / 我的退款单
 * memberId 从网关透传的 X-User-Id 获取，不信任前端
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    /** 模拟第三方支付回调（演示入口；生产由支付宝/微信异步通知替换），重复回调幂等返回 */
    @PostMapping("/{paymentSn}/mock-callback")
    public Result<PaymentDTO> mockCallback(@PathVariable String paymentSn,
                                           @RequestParam(required = false) String tradeNo) {
        return Result.success(paymentService.mockCallback(paymentSn, tradeNo));
    }

    /** 支付结果查询（收银台/支付结果页轮询；含查单兜底补偿回写） */
    @GetMapping("/query")
    public Result<PaymentDTO> query(@RequestHeader("X-User-Id") Long memberId,
                                    @RequestParam("orderSn") String orderSn) {
        return Result.success(paymentService.queryByOrder(memberId, orderSn));
    }

    /** 申请退款（整单退款；仅退款/退货退款） */
    @PostMapping("/refund/apply")
    public Result<Void> applyRefund(@RequestHeader("X-User-Id") Long memberId,
                                    @Valid @RequestBody RefundApplyDTO dto) {
        refundService.apply(memberId, dto);
        return Result.success();
    }

    /** 我的退款单分页 */
    @GetMapping("/refund/list")
    public Result<Page<Map<String, Object>>> myRefunds(@RequestHeader("X-User-Id") Long memberId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return Result.success(refundService.pageMine(memberId, page, size));
    }
}
