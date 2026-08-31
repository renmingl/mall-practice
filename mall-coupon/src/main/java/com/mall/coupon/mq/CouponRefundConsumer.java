package com.mall.coupon.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 退款退券消费者（阶段 6 退款联动）：payment 退款成功后经本地消息表发 mall-coupon-refund-topic，
 * 消息体 {orderId, memberId}（payment 持有订单与会员信息），本服务消费后按订单核销记录退券（2→0，过期置3；幂等）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Configuration("couponRefundConsumerConfig")
@RequiredArgsConstructor
public class CouponRefundConsumer {

    private final CouponService couponService;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> couponRefundConsumer() {
        return message -> {
            try {
                Map<String, Object> body = objectMapper.readValue(message.getPayload(), Map.class);
                Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
                Long memberId = Long.valueOf(String.valueOf(body.get("memberId")));
                log.info("退款退券消费开始 orderId={} memberId={}", orderId, memberId);
                couponService.refund(orderId, memberId);
                log.info("退款退券完成 orderId={} memberId={}", orderId, memberId);
            } catch (Exception e) {
                log.error("退款退券消费失败", e);
                throw new RuntimeException(e);
            }
        };
    }
}
