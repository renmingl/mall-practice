package com.mall.order.task;

import com.mall.order.service.OrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单定时任务（阶段 5/6 兜底）：
 * 1) 超时关单扫描：延迟消息丢失/发送失败的兜底（每 5 分钟）
 * 2) 超时自动收货：发货 15 天未确认自动完成（每 5 分钟）
 * 双通道：xxl-job 集中调度（orderCloseScan/orderAutoReceive）+ @Scheduled 本地兜底，幂等条件更新重复执行无害
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderService orderService;

    /** 兜底关单：待付款超 30 分钟自动取消 + 回补库存/退券 */
    @XxlJob("orderCloseScan")
    @Scheduled(cron = "0 0/5 * * * ?")
    public void closeExpiredOrders() {
        try {
            orderService.closeExpiredOrders();
        } catch (Exception e) {
            log.error("兜底关单扫描异常", e);
        }
    }

    /** 自动收货：发货超 15 天未确认收货自动完成 */
    @XxlJob("orderAutoReceive")
    @Scheduled(cron = "0 0/5 * * * ?")
    public void autoReceiveOrders() {
        try {
            orderService.autoReceive();
        } catch (Exception e) {
            log.error("自动收货扫描异常", e);
        }
    }
}
