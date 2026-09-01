package com.mall.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ES 客户端（阶段 8 13.2）：co.elastic.clients 官方 Java Client（8.17.x）
 * ES 无 Spring Boot 自动配置，手写 RestClient → Transport → Client 三级构建；
 * JacksonJsonpMapper 复用 Web 层 Jackson 序列化，避免引入 jakarta.json 绑定
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.uris:http://127.0.0.1:9200}")
    private String uris;

    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(HttpHost.create(uris)).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
