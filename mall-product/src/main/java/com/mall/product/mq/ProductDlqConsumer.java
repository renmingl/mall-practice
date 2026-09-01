package com.mall.product.mq;

import com.mall.common.mq.DeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 商品服务死信队列消费者（8.5 落地）：退款回补库存消费重试耗尽（max-attempts=3）后 RocketMQ 自动投递
 * %DLQ%product-group 死信主题，统一落 mq_dead_letter 表 + error 日志供人工介入；
 * 死信消费者自身不重试（max-attempts=1）且吞异常，避免死信套死信。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@Slf4j
@Configuration("productDlqConsumerConfig")
@RequiredArgsConstructor
public class ProductDlqConsumer {

    private final DeadLetterService deadLetterService;

    /** 退款回补库存死信（%DLQ%product-group） */
    @Bean
    public Consumer<Message<String>> dlqStockRefundConsumer() {
        return message -> {
            try {
                deadLetterService.save("product-group", "%DLQ%product-group", message);
            } catch (Exception e) {
                log.error("退款回补库存死信落库失败（死信丢失风险，请人工核对日志）", e);
            }
        };
    }
}
