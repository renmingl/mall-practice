package com.mall.member.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.mq.MqSender;
import com.mall.common.mq.MqTopics;
import com.mall.member.service.MemberPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 积分变动消费者（阶段 6）：payment 支付成功/退款成功后经本地消息表投递 mall-member-points-topic
 * 消息体 {memberId, orderSn, payAmount}，tag=PAID 返积分 / tag=REFUND 扣回；
 * 幂等：MemberPointsService 按 order_sn + change_type 判重（MQ 至少一次投递）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemberPointsConsumer {

    private final MemberPointsService memberPointsService;
    private final ObjectMapper objectMapper;

    @Bean
    public Consumer<Message<String>> pointsConsumer() {
        return message -> {
            try {
                Map<String, Object> body = objectMapper.readValue(message.getPayload(), Map.class);
                Long memberId = Long.valueOf(String.valueOf(body.get("memberId")));
                String orderSn = String.valueOf(body.get("orderSn"));
                BigDecimal payAmount = new BigDecimal(String.valueOf(body.get("payAmount")));
                String tag = message.getHeaders().get(MqSender.ROCKETMQ_TAGS_HEADER, String.class);
                if (MqTopics.TAG_PAID.equals(tag)) {
                    memberPointsService.earn(memberId, orderSn, payAmount);
                } else if (MqTopics.TAG_REFUND.equals(tag)) {
                    memberPointsService.deduct(memberId, orderSn, payAmount);
                } else {
                    log.warn("积分消息 tag 未知，忽略 tag={} orderSn={}", tag, orderSn);
                }
            } catch (Exception e) {
                log.error("积分变动消费失败", e);
                throw new RuntimeException(e);
            }
        };
    }
}
