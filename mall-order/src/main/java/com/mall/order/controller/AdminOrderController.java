package com.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台订单管理（阶段 6 履约）：分页查询 + 发货
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /** 订单分页（按订单号/状态筛选） */
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) String orderSn,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.adminPage(orderSn, status, page, size));
    }

    /** 发货：1待发货 → 2待收货 + 物流信息 */
    @PostMapping("/deliver")
    public Result<Void> deliver(@RequestParam Long orderId,
                                @RequestParam String company,
                                @RequestParam String sn) {
        orderService.deliver(orderId, company, sn);
        return Result.success();
    }
}
