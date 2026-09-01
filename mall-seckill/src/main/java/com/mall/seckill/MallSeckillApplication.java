package com.mall.seckill;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 秒杀服务启动类（端口 9000）
 * @EnableDubbo：暴露 SeckillDubboService（核心链路 RPC，README 演进路线第二阶段）
 * @EnableFeignClients：调 product 拉 SKU 快照 / 校验库存
 * @EnableScheduling：定时预热
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@EnableDubbo
@EnableScheduling
@MapperScan("com.mall.mbg.mapper")
@EnableFeignClients(basePackages = "com.mall.api")   // 阶段 7：秒杀配置校验/商品列表调 product 契约
@SpringBootApplication
public class MallSeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallSeckillApplication.class, args);
    }
}
