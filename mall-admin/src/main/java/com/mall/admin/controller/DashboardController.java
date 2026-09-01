package com.mall.admin.controller;

import com.mall.admin.service.DashboardService;
import com.mall.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台数据看板（10.1～10.6）：今日概览 / 会员运营 / 订单趋势 / 销量榜 / 浏览榜 / 库存预警
 * 聚合各服务内部统计接口（order/member/product），网关鉴权后调用
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 看板总览（一次聚合返回全部指标） */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(dashboardService.summary());
    }
}
