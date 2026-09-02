package com.mall.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 助手服务启动类（端口 9200）
 * 阶段 9 16.3：@MapperScan 扫 ai_chat_message 表 Mapper（历史入库）；@EnableFeignClients 扫 mall-api 契约（业务数据问答供给）
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@MapperScan("com.mall.mbg.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.mall.api")
public class MallAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAiApplication.class, args);
    }
}
