package com.mall.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购物车服务启动类（端口 8600）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.mall.api")   // 阶段 4：列表组装调 product 查 SKU 快照
public class MallCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallCartApplication.class, args);
    }
}
