package com.mall.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台退款审核（阶段 6）：退款单分页 / 审核通过或拒绝 / 确认退货（退货退款）
 * 仅退款：审核通过即执行退款（0→1→3→4）；退货退款：审核通过（1）→ 确认退货（3）→ 执行退款（4）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/admin/refund")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;

    /** 退款单分页（按订单号/状态筛选；status 0申请中 1审核通过 2退货中 3退款中 4已退款 5已拒绝） */
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) String orderSn,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(refundService.adminPage(orderSn, status, page, size));
    }

    /** 审核退款申请：approved=true 通过 / false 拒绝 */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                              @RequestParam("approved") boolean approved,
                              @RequestParam("auditBy") String auditBy) {
        refundService.audit(id, approved, auditBy);
        return Result.success();
    }

    /** 确认退货（退货退款：审核通过后，后台确认收到退货后执行退款） */
    @PostMapping("/{id}/confirm-return")
    public Result<Void> confirmReturn(@PathVariable Long id) {
        refundService.confirmReturn(id);
        return Result.success();
    }

    /** 重试执行退款（仅退款执行失败/超时后的补偿入口；退款单停留 1 或 3 状态时可触发） */
    @PostMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        refundService.retryExecute(id);
        return Result.success();
    }
}
