package com.mall.member;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 会员服务启动类（端口 8400）
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@MapperScan({"com.mall.mbg.mapper", "com.mall.member.mapper"})
@SpringBootApplication
public class MallMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallMemberApplication.class, args);
    }
}
