package com.mall.order.controller;

import com.mall.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单分库分表演示（阶段 8 13.5）：ShardingSphere 与 Boot 4 未官方适配，学习项目以逻辑分表演示兜底
 * 路由算法与 ShardingSphere 标准 MOD 分片一致（sharding-value % sharding-count）；
 * 分片键 member_id：同一买家订单落同一物理表（orders_0 ~ orders_3），按订单号查询需携带 member_id 路由
 * @author renmingl
 * @date 2026-09-01 16:20:00
 */
@RestController
@RequestMapping("/api/admin/order/sharding")
public class OrderShardingDemoController {

    /** 分片数量（等价 ShardingSphere 配置 sharding-algorithms.orders.type=MOD, sharding-count=4） */
    private static final int SHARD_COUNT = 4;

    /** 分表路由演示：member_id 取模定位物理表，并给出对应的查询 SQL（含唯一索引失效说明） */
    @GetMapping("/demo")
    public Result<Map<String, Object>> demo(@RequestParam Long memberId) {
        int shard = Math.floorMod(memberId, SHARD_COUNT);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", memberId);
        data.put("shardingKey", "member_id");
        data.put("algorithm", "member_id % " + SHARD_COUNT + "（ShardingSphere MOD 算法）");
        data.put("table", "orders_" + shard);
        data.put("querySql", "SELECT * FROM orders_" + shard
                + " WHERE member_id = " + memberId + " AND order_sn = ?");
        data.put("notice", "分表后 orders.request_id / order_sn 唯一索引仅单表内生效：按订单号查询必须携带 member_id 路由，"
                + "否则需全表扫描聚合（生产建议 gateway 按 member_id 路由或引入 order_sn → member_id 映射表）");
        return Result.success(data);
    }
}
