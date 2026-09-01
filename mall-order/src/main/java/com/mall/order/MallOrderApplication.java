package com.mall.order;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类（端口 8700）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@MapperScan("com.mall.mbg.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "com.mall.api")   // 阶段 5：下单编排调 cart/product/coupon/payment 契约
@EnableScheduling   // 阶段 5：超时关单兑底扫描 + 超时自动收货
@EnableDubbo        // 阶段 7：秒杀落单核验/关单回补经 Dubbo 调 seckill（dubbo.enabled=false 时自动降级 Feign）
public class MallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderApplication.class, args);
    }
}
