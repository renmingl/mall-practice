package com.mall.common.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

/**
 * MQ 发送器：基于 Spring Cloud Stream 的 StreamBridge 动态发送（无需预定义 binding）
 * RocketMQ 扩展头：rocketmq_TAGS 消息标签、rocketmq_DELAY 延迟级别（16=30 分钟）
 * 说明：消息体统一 JSON 字符串，消费端以 Message&lt;String&gt; 接收后自行反序列化
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
public class MqSender {

    /** RocketMQ 标签头 */
    public static final String ROCKETMQ_TAGS_HEADER = "rocketmq_TAGS";

    /** RocketMQ 延迟级别头 */
    public static final String ROCKETMQ_DELAY_HEADER = "rocketmq_DELAY";

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;

    public MqSender(StreamBridge streamBridge, ObjectMapper objectMapper) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
    }

    /** 同步发送（失败抛异常，由调用方决定重试/落本地消息表） */
    public void send(String topic, String tag, Object payload) {
        if (!trySend(topic, tag, payload)) {
            throw new IllegalStateException("MQ 发送失败 topic=" + topic + " tag=" + tag);
        }
    }

    /** 发送（返回是否成功，供本地消息表标记状态） */
    public boolean trySend(String topic, String tag, Object payload) {
        try {
            return streamBridge.send(topic, buildMessage(tag, payload, 0));
        } catch (Exception e) {
            log.error("MQ 发送异常 topic={} tag={}", topic, tag, e);
            return false;
        }
    }

    /** 延迟发送（RocketMQ 延迟级别：16=30 分钟，超时关单用） */
    public boolean trySendDelay(String topic, String tag, Object payload, int delayLevel) {
        try {
            return streamBridge.send(topic, buildMessage(tag, payload, delayLevel));
        } catch (Exception e) {
            log.error("MQ 延迟发送异常 topic={} tag={} delay={}", topic, tag, delayLevel, e);
            return false;
        }
    }

    private Message<String> buildMessage(String tag, Object payload, int delayLevel) throws JsonProcessingException {
        String body = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        MessageBuilder<String> builder = MessageBuilder.withPayload(body)
                .setHeader(MessageHeaders.CONTENT_TYPE, "application/json")
                .setHeader(ROCKETMQ_TAGS_HEADER, tag);
        if (delayLevel > 0) {
            builder.setHeader(ROCKETMQ_DELAY_HEADER, delayLevel);
        }
        return builder.build();
    }
}
