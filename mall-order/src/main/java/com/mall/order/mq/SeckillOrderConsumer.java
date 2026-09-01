package com.mall.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.order.dto.SeckillOrderMsg;
import com.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * 秒杀落单消费者（14.5 MQ 削峰）：seckill 提交成功（Lua 扣减）后投递本 Topic，
 * order 异步建单（order_type=2）；消费逻辑幂等（requestId 查重），核验失败/扣减失败
 * 已写 Redis 结果（status=2）不再抛出，仅未知异常上抛触发 MQ 重试（重试经 requestId 幂等自愈）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /** 秒杀削峰落单消费者：body=SeckillOrderMsg JSON（seckillProductId/sessionId/memberId/quantity/requestId/收货信息） */
    @Bean
    public Consumer<Message<String>> seckillOrderConsumer() {
        return message -> {
            SeckillOrderMsg msg;
            try {
                msg = objectMapper.readValue(message.getPayload(), SeckillOrderMsg.class);
            } catch (Exception e) {
                log.error("秒杀落单消息反序列化失败，跳过", e);
                return; // 消息体损坏，重试无意义，跳过避免死循环
            }
            try {
                orderService.createSeckillOrder(msg);
            } catch (Exception e) {
                // 未知异常（DB 不可用等）上抛触发 MQ 重试；重试时 requestId 幂等 + 核验兜底自愈
                log.error("秒杀落单消息消费失败 requestId={}", msg.getRequestId(), e);
                throw new RuntimeException(e);
            }
        };
    }
}
