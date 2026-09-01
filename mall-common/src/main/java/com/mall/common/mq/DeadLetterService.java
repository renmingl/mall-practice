package com.mall.common.mq;

import com.mall.mbg.entity.MqDeadLetter;
import com.mall.mbg.mapper.MqDeadLetterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.time.LocalDateTime;

/**
 * 死信落库组件（8.5 死信队列）：消费重试耗尽进入 %DLQ%{group} 主题后，各服务 DLQ 消费者
 * 调用本组件把死信落 mq_dead_letter 表 + error 日志，人工在表/日志中介入补偿，
 * 避免无限重试阻塞消费。
 * 装配方式同 TxMessageService（MqAutoConfiguration 条件装配，见 MqAutoConfiguration）。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@Slf4j
public class DeadLetterService {

    private final MqDeadLetterMapper deadLetterMapper;

    public DeadLetterService(MqDeadLetterMapper deadLetterMapper) {
        this.deadLetterMapper = deadLetterMapper;
    }

    /**
     * 记录死信：consumerGroup 由调用方（各服务 DLQ 消费者）传入，topic 取 DLQ 主题名；
     * 消息体为原始消息 payload（String），失败原因取消息头 x-exception-message（binder 附加）。
     */
    public void save(String consumerGroup, String topic, Message<String> message) {
        String body = message == null ? null : message.getPayload();
        MessageHeaders headers = message == null ? null : message.getHeaders();
        String error = headers == null ? null : String.valueOf(headers.get("x-exception-message"));
        if (body != null && body.length() > 60000) {
            body = body.substring(0, 60000);
        }
        MqDeadLetter deadLetter = new MqDeadLetter();
        deadLetter.setConsumerGroup(consumerGroup);
        deadLetter.setTopic(topic);
        deadLetter.setMessageBody(body);
        deadLetter.setErrorInfo(error == null || "null".equals(error) ? null
                : error.length() > 1000 ? error.substring(0, 1000) : error);
        deadLetter.setStatus((byte) 0);
        deadLetter.setCreateTime(LocalDateTime.now());
        deadLetterMapper.insert(deadLetter);
        log.error("MQ 死信已落库待人工介入：id={} consumerGroup={} topic={} error={}",
                deadLetter.getId(), consumerGroup, topic, deadLetter.getErrorInfo());
    }
}
