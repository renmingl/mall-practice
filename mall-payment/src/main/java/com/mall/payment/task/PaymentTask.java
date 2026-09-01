package com.mall.payment.task;

import com.mall.common.mq.MqTopics;
import com.mall.common.mq.TxMessageService;
import com.mall.payment.service.PaymentService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付服务定时任务（阶段 6 兜底）：
 * 1) 本地消息表补发：退款联动/返积分消息发送失败或宕机场景（每 5 分钟）
 * 2) 支付回写补偿：支付单已成功但订单未回写（回调后 Feign 失败/宕机场景，每 5 分钟）
 * 双通道：xxl-job 集中调度（paymentResend/paymentWriteBack）+ @Scheduled 本地兜底，任务幂等重复执行无副作用
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTask {

    private final TxMessageService txMessageService;
    private final PaymentService paymentService;

    /** 本地消息表补发（tx_message status 0/2 且重试未超上限） */
    @XxlJob("paymentResend")
    @Scheduled(cron = "0 0/5 * * * ?")
    public void resendPending() {
        try {
            int count = txMessageService.resendPending(MqTopics.TX_MESSAGE_MAX_RETRY);
            if (count > 0) {
                log.info("本地消息表补发完成 count={}", count);
            }
        } catch (Exception e) {
            log.error("本地消息表补发扫描异常", e);
        }
    }

    /** 支付回写补偿：支付单已成功但订单仍待付款 → 补 markPaid */
    @XxlJob("paymentWriteBack")
    @Scheduled(cron = "0 0/5 * * * ?")
    public void compensateWriteBack() {
        try {
            paymentService.compensateWriteBack();
        } catch (Exception e) {
            log.error("支付回写补偿扫描异常", e);
        }
    }
}
