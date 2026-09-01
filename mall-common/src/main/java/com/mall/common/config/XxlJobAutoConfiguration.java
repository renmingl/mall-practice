package com.mall.common.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * xxl-job 执行器自动配置：只有配置了 xxl.job.admin.addresses 的服务才注册执行器
 * （order/payment/product/coupon/seckill 接入；未配置的服务仅走本地 @Scheduled 兜底）。
 * 设计：任务方法同时挂 @Scheduled（本地兜底，调度中心未启动任务不中断）与 @XxlJob（集中调度/手动触发），
 * 两者幂等重复执行无害（关单/过期/补发扫描均为幂等条件更新）。
 * @author renmingl
 * @date 2026-09-01 14:00:00
 */
@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@ConditionalOnProperty(prefix = "xxl.job", name = "admin.addresses")
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XxlJobAutoConfiguration.class);

    /** 执行器注册：调度中心按 executor.appname 识别执行器（与 xxl_job.sql 预置的 xxl_job_group.app_name 一致） */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        log.info("xxl-job 执行器注册：appname={} admin={} port={}",
                properties.getExecutor().getAppname(), properties.getAdmin().getAddresses(), properties.getExecutor().getPort());
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getExecutor().getAppname());
        executor.setIp(properties.getExecutor().getIp());
        executor.setPort(properties.getExecutor().getPort());
        executor.setLogPath(properties.getExecutor().getLogPath());
        executor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());
        return executor;
    }
}
