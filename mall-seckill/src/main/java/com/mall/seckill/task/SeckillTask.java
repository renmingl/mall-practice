package com.mall.seckill.task;

import com.mall.seckill.service.SeckillService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀定时任务（14.3 自动预热）：
 * 每 30 秒扫描即将开始（10 分钟内）的启用场次自动预热（DB → Redis），防人工漏预热
 * 双通道：xxl-job 集中调度（seckillPreheat）+ @Scheduled 本地兜底，预热标记防重复幂等
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillTask {

    private final SeckillService seckillService;

    /** 自动预热：活动开始前 10 分钟内的启用场次（预热标记防重复，30 秒一轮） */
    @XxlJob("seckillPreheat")
    @Scheduled(fixedDelay = 30_000)
    public void preheatUpcoming() {
        try {
            int count = seckillService.preheatUpcoming();
            if (count > 0) {
                log.info("定时预热完成 count={}", count);
            }
        } catch (Exception e) {
            log.error("定时预热异常", e);
        }
    }
}
