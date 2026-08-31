package com.mall.common.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.mbg.entity.TxMessage;
import com.mall.mbg.mapper.TxMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 本地消息表组件（可靠消息最终一致性）
 * 用法：业务事务内调用 {@link #saveAndSendOnCommit}（写业务表 + 写 tx_message 同一本地事务），
 * 事务提交后自动发送 MQ；发送失败留待定时任务 {@link #resendPending} 扫描补发。
 * 幂等：tx_message.uk_biz_id 防重复落表；消费端按业务键查重防重复消费。
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
public class TxMessageService {

    private final TxMessageMapper txMessageMapper;
    private final MqSender mqSender;
    private final ObjectMapper objectMapper;

    public TxMessageService(TxMessageMapper txMessageMapper, MqSender mqSender, ObjectMapper objectMapper) {
        this.txMessageMapper = txMessageMapper;
        this.mqSender = mqSender;
        this.objectMapper = objectMapper;
    }

    /**
     * 业务事务内调用：保存本地消息并注册事务提交后发送回调。
     * 必须在事务内调用（无事务时直接发送，不落表）。
     */
    public void saveAndSendOnCommit(String bizId, String topic, String tag, Object payload) {
        String body = toJson(payload);
        // 幂等：同 bizId+topic 已存在则跳过（uk_biz_id 兜底防并发重复插入）
        Long exists = txMessageMapper.selectCount(Wrappers.<TxMessage>lambdaQuery()
                .eq(TxMessage::getBizId, bizId)
                .eq(TxMessage::getTopic, topic));
        if (exists != null && exists > 0) {
            return;
        }
        TxMessage message = new TxMessage();
        message.setBizId(bizId);
        message.setTopic(topic);
        message.setTag(tag);
        message.setMessageBody(body);
        message.setStatus((byte) 0);
        message.setRetryCount(0);
        txMessageMapper.insert(message);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务兜底：直接发送
            sendMessage(message.getId());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendMessage(message.getId());
            }
        });
    }

    /** 发送指定消息并更新状态：成功置 1，失败置 2 并累加重试次数 */
    public void sendMessage(Long id) {
        TxMessage message = txMessageMapper.selectById(id);
        if (message == null) {
            return;
        }
        boolean ok = mqSender.trySend(message.getTopic(), message.getTag(), message.getMessageBody());
        TxMessage update = new TxMessage();
        update.setId(id);
        update.setStatus((byte) (ok ? 1 : 2));
        update.setRetryCount(message.getRetryCount() + 1);
        txMessageMapper.updateById(update);
        if (!ok) {
            log.warn("本地消息发送失败，待扫描补发: bizId={} topic={} retry={}",
                    message.getBizId(), message.getTopic(), message.getRetryCount() + 1);
        }
    }

    /** 扫描补发：status IN (0待发送, 2发送失败) 且重试未超上限，供服务定时任务调用 */
    public int resendPending(int maxRetry) {
        List<TxMessage> pending = txMessageMapper.selectList(Wrappers.<TxMessage>lambdaQuery()
                .in(TxMessage::getStatus, (byte) 0, (byte) 2)
                .lt(TxMessage::getRetryCount, maxRetry)
                .orderByAsc(TxMessage::getId)
                .last("LIMIT 100"));
        pending.forEach(m -> sendMessage(m.getId()));
        return pending.size();
    }

    private String toJson(Object payload) {
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("消息体序列化失败", e);
        }
    }
}
