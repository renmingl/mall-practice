package com.mall.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 订单 MQ 消费者（阶段 5/6）：
 * orderCloseConsumer  — 延迟关单（下单 30 分钟后未支付自动关单，定时扫描兜底）
 * orderRefundConsumer — 退款成功回写（payment 退款成功后经本地消息表通知，Feign 回写双保险）
 * 消费逻辑均走幂等条件更新（订单状态前置校验），重复消费无副作用
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OrderMqConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /** 延迟关单消费者：body={orderSn} */
    @Bean
    public Consumer<Message<String>> orderCloseConsumer() {
        return message -> {
            try {
                Map<String, Object> body = objectMapper.readValue(message.getPayload(), Map.class);
                String orderSn = String.valueOf(body.get("orderSn"));
                log.info("延迟关单消息消费 orderSn={}", orderSn);
                orderService.closeExpired(orderSn);
            } catch (Exception e) {
                log.error("延迟关单消息消费失败", e);
                throw new RuntimeException(e);
            }
        };
    }

    /** 退款成功回写订单消费者：body={orderSn}（markRefunded 幂等） */
    @Bean
    public Consumer<Message<String>> orderRefundConsumer() {
        return message -> {
            try {
                Map<String, Object> body = objectMapper.readValue(message.getPayload(), Map.class);
                String orderSn = String.valueOf(body.get("orderSn"));
                log.info("退款回写订单消息消费 orderSn={}", orderSn);
                orderService.markRefunded(orderSn);
            } catch (Exception e) {
                log.error("退款回写订单消息消费失败", e);
                throw new RuntimeException(e);
            }
        };
    }
}
