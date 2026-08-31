package com.mall.product.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.product.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 退款回补库存消费者（阶段 6 退款联动）：payment 退款成功后经本地消息表发 mall-stock-refund-topic，
 * 消息体含订单明细（orderSn + items），本服务消费后逐条回补（change_type=3 退款回补，幂等）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Configuration("stockRefundConsumerConfig")
@RequiredArgsConstructor
public class StockRefundConsumer {

    private final StockService stockService;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> stockRefundConsumer() {
        return message -> {
            try {
                Map<String, Object> body = objectMapper.readValue(message.getPayload(), Map.class);
                String orderSn = String.valueOf(body.get("orderSn"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
                log.info("退款回补库存消费开始 orderSn={}", orderSn);
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Long skuId = Long.valueOf(String.valueOf(item.get("skuId")));
                        int quantity = Integer.parseInt(String.valueOf(item.get("quantity")));
                        stockService.releaseStock(orderSn, skuId, quantity, 3);
                    }
                }
                log.info("退款回补库存完成 orderSn={}", orderSn);
            } catch (Exception e) {
                log.error("退款回补库存消费失败", e);
                throw new RuntimeException(e);
            }
        };
    }
}
