package com.mall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商品服务启动类（端口 8500）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@MapperScan("com.mall.mbg.mapper")
@SpringBootApplication
@ConfigurationPropertiesScan   // 阶段 3：注册 OssProperties 等配置类
@EnableScheduling   // 阶段 3：缓存预热定时任务（xxl-job 接入后替换为执行器任务）
public class MallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallProductApplication.class, args);
    }
}
