package com.mall.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 多模型配置（OpenAI 兼容协议多供应商）
 * 可用性 = 配置存在且 api-key 非空；key 由使用者自配（环境变量注入 docker/.env），
 * 未配置 key 的模型自动禁用并在 /api/ai/config 中标记 available=false
 * @author renmingl
 * @date 2026-09-02 13:42:22
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 上游 HTTP 调用读超时（默认 60s） */
    private Duration timeout = Duration.ofSeconds(60);

    /** 模型供应商表：key = provider id（deepseek/qwen/openai/zhipu，可自行增删） */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Provider {

        /** 展示名（前端模型选择器 label） */
        private String label;

        /** OpenAI 兼容接口地址（到 /v1 为止，如 https://api.deepseek.com/v1） */
        private String baseUrl;

        /** 使用者自配的 API Key（空 = 该模型不可用） */
        private String apiKey;

        /** 模型名（如 deepseek-chat / qwen-plus / gpt-4o-mini） */
        private String model;
    }
}
