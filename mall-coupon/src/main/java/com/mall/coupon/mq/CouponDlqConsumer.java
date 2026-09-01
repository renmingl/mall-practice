package com.mall.coupon.mq;

import com.mall.common.mq.DeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 优惠券服务死信队列消费者（8.5 落地）：退款退券消费重试耗尽（max-attempts=3）后 RocketMQ 自动投递
 * %DLQ%coupon-group 死信主题，统一落 mq_dead_letter 表 + error 日志供人工介入；
 * 死信消费者自身不重试（max-attempts=1）且吞异常，避免死信套死信。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@Slf4j
@Configuration("couponDlqConsumerConfig")
@RequiredArgsConstructor
public class CouponDlqConsumer {

    private final DeadLetterService deadLetterService;

    /** 退款退券死信（%DLQ%coupon-group） */
    @Bean
    public Consumer<Message<String>> dlqCouponRefundConsumer() {
        return message -> {
            try {
                deadLetterService.save("coupon-group", "%DLQ%coupon-group", message);
            } catch (Exception e) {
                log.error("退款退券死信落库失败（死信丢失风险，请人工核对日志）", e);
            }
        };
    }
}
