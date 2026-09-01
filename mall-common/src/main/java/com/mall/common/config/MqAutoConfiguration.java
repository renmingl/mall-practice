package com.mall.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.mq.DeadLetterService;
import com.mall.common.mq.MqSender;
import com.mall.common.mq.TxMessageService;
import com.mall.mbg.mapper.MqDeadLetterMapper;
import com.mall.mbg.mapper.TxMessageMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

/**
 * MQ 自动配置：MqSender（StreamBridge 封装）、TxMessageService（本地消息表组件）、
 * DeadLetterService（死信落库组件，8.5）。
 * 条件装配：只有引入 spring-cloud-starter-stream-rocketmq 的服务才装配 MqSender；
 * 只有显式依赖 mall-mbg 且 @MapperScan 扫描到对应 Mapper 的服务才装配 TxMessageService/DeadLetterService。
 * 注册入口：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@AutoConfiguration
@ConditionalOnClass(StreamBridge.class)
public class MqAutoConfiguration {

    /** 统一 MQ 发送器：动态 destination 发送（含 tag/延迟头） */
    @Bean
    @ConditionalOnMissingBean
    public MqSender mqSender(StreamBridge streamBridge, ObjectMapper objectMapper) {
        return new MqSender(streamBridge, objectMapper);
    }

    /** 本地消息表组件：事务内落表 + afterCommit 发送 + 定时扫描补发。
     * 装配条件：仅依赖 mall-mbg（classpath 有 TxMessageMapper）且 Mapper 扫描到 TxMessageMapper 的服务生效；
     * 不依赖 @ConditionalOnBean（MyBatis MapperFactoryBean 在条件评估阶段实例化需 SqlSessionFactory，评估不可靠）。 */
    @Bean
    @ConditionalOnClass(TxMessageMapper.class)
    @ConditionalOnMissingBean
    public TxMessageService txMessageService(TxMessageMapper txMessageMapper, MqSender mqSender, ObjectMapper objectMapper) {
        return new TxMessageService(txMessageMapper, mqSender, objectMapper);
    }

    /** 死信落库组件（8.5）：%DLQ%{group} 死信消费者落 mq_dead_letter 表。
     * 装配条件：同 TxMessageService（classpath 有 MqDeadLetterMapper 且 Mapper 扫描到的服务生效） */
    @Bean
    @ConditionalOnClass(MqDeadLetterMapper.class)
    @ConditionalOnMissingBean
    public DeadLetterService deadLetterService(MqDeadLetterMapper mqDeadLetterMapper) {
        return new DeadLetterService(mqDeadLetterMapper);
    }
}
