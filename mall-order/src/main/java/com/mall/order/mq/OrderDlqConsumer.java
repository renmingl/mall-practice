package com.mall.order.mq;

import com.mall.common.mq.DeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 订单服务死信队列消费者（8.5 落地）：消费重试耗尽（max-attempts=3）后 RocketMQ 自动投递
 * %DLQ%{group} 死信主题，本类三个消费者分别对应 order-close-group / order-refund-group /
 * seckill-order-group，统一落 mq_dead_letter 表 + error 日志供人工介入；
 * 死信消费者自身不重试（max-attempts=1）且吞异常，避免死信套死信。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderDlqConsumer {

    private final DeadLetterService deadLetterService;

    /** 关单死信（%DLQ%order-close-group） */
    @Bean
    public Consumer<Message<String>> dlqOrderCloseConsumer() {
        return message -> {
            try {
                deadLetterService.save("order-close-group", "%DLQ%order-close-group", message);
            } catch (Exception e) {
                log.error("关单死信落库失败（死信丢失风险，请人工核对日志）", e);
            }
        };
    }

    /** 退款回写死信（%DLQ%order-refund-group） */
    @Bean
    public Consumer<Message<String>> dlqOrderRefundConsumer() {
        return message -> {
            try {
                deadLetterService.save("order-refund-group", "%DLQ%order-refund-group", message);
            } catch (Exception e) {
                log.error("退款回写死信落库失败（死信丢失风险，请人工核对日志）", e);
            }
        };
    }

    /** 秒杀落单死信（%DLQ%seckill-order-group） */
    @Bean
    public Consumer<Message<String>> dlqSeckillOrderConsumer() {
        return message -> {
            try {
                deadLetterService.save("seckill-order-group", "%DLQ%seckill-order-group", message);
            } catch (Exception e) {
                log.error("秒杀落单死信落库失败（死信丢失风险，请人工核对日志）", e);
            }
        };
    }
}
