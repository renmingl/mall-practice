package com.mall.seckill.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则（12.1 接口限流 + 热点参数限流）：
 * 秒杀提交接口为流量洪峰入口，QPS 限流（资源级）+ 热点参数限流（按秒杀商品维度，需将
 * seckillProductId 显式作为方法参数后取消注释 paramFlowRule 即可生效，学习项目先演示资源级限流）
 * 规则启动时硬编码加载（学习项目从简；生产可接 Sentinel Dashboard 动态下发）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Configuration
public class SentinelConfig {

    /** 秒杀提交接口资源名（与 @SentinelResource 一致） */
    public static final String SECKILL_SUBMIT_RESOURCE = "seckill:submit";

    /** 秒杀提交接口 QPS 阈值 */
    public static final int SECKILL_SUBMIT_QPS = 100;

    @PostConstruct
    public void init() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule submitRule = new FlowRule();
        submitRule.setResource(SECKILL_SUBMIT_RESOURCE);
        submitRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        submitRule.setCount(SECKILL_SUBMIT_QPS);
        submitRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(submitRule);
        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 限流规则已加载：{} QPS={}", SECKILL_SUBMIT_RESOURCE, SECKILL_SUBMIT_QPS);
    }
}
