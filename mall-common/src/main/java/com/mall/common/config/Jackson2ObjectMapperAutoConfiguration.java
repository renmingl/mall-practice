package com.mall.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 2（com.fasterxml）ObjectMapper 自动配置。
 * 背景：Spring Boot 4 默认仅自动配置 Jackson 3（tools.jackson）的 ObjectMapper（HTTP 消息转换器用），
 * 而项目 MQ 组件（MqSender/TxMessageService/各消息消费者）统一使用 Jackson 2 序列化消息体，
 * 两者类型不同互不冲突，此处显式提供 Jackson 2 bean 供 MQ 组件注入。
 * 注册入口：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * @author renmingl
 * @date 2026-08-31 16:00:00
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class Jackson2ObjectMapperAutoConfiguration {

    /** Jackson 2 ObjectMapper：MQ 消息体 JSON 序列化/反序列化（MQ 组件专用） */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
