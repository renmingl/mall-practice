package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品运营数据（场景 10.2 / 10.5 / 10.6，纯 Redis）：
 * PV：INCR page:view:{spuId}；UV：HyperLogLog PFADD/PFCOUNT（亿级去重，误差 0.81%，12KB 内存）；
 * 浏览排行：ZSET rank:views ZINCRBY（ZSET 本质排序树，ZREVRANGE 取 Top N）；
 * 点赞：Set like:{spuId}（SADD/SREM/SCARD/SISMEMBER，天然幂等）；
 * 足迹：ZSET history:{memberId}（score=浏览时间戳，ZREVRANGE 最近浏览 + ZREMRANGEBYRANK 截断 50）
 * 为什么不用 MySQL 计数：PV/点赞高频写（行锁热点/写放大），Redis 计数器异步落库（product_spu.sales 定时回写）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductStatsService {

    /** PV 计数 key 前缀：page:view:{spuId} */
    public static final String KEY_PV = "page:view:";

    /** UV HyperLogLog key 前缀：page:uv:{spuId} */
    public static final String KEY_UV = "page:uv:";

    /** 浏览排行 ZSET（member=spuId，score=PV）：rank:views */
    public static final String KEY_VIEWS_RANK = "rank:views";

    /** 点赞 Set key 前缀：like:{spuId} */
    public static final String KEY_LIKE = "like:";

    /** 浏览足迹 ZSET key 前缀：history:{memberId} */
    public static final String KEY_HISTORY = "history:";

    /** 足迹保留条数（最近 50 条） */
    public static final int HISTORY_LIMIT = 50;

    private final StringRedisTemplate redisTemplate;
    private final ProductSpuMapper spuMapper;

    // ==================== PV / UV（10.2） ====================

    /** 浏览埋点：PV 自增 + UV HyperLogLog 去重 + 浏览排行累计（未登录按设备匿名 ID 去重） */
    public void trackView(Long spuId, String userId) {
        String uid = userId == null || userId.isBlank() ? "anon:" + java.util.UUID.randomUUID() : userId;
        redisTemplate.opsForValue().increment(KEY_PV + spuId);
        redisTemplate.opsForHyperLogLog().add(KEY_UV + spuId, uid);
        redisTemplate.opsForZSet().incrementScore(KEY_VIEWS_RANK, String.valueOf(spuId), 1);
    }

    /** PV / UV 查询（看板商品统计） */
    public Map<String, Object> pvUv(Long spuId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("spuId", spuId);
        String pv = redisTemplate.opsForValue().get(KEY_PV + spuId);
        row.put("pv", pv == null ? 0 : Long.parseLong(pv));
        Long uv = redisTemplate.opsForHyperLogLog().size(KEY_UV + spuId);
        row.put("uv", uv == null ? 0 : uv);
        return row;
    }

    /** 浏览排行 Top N（ZSET rank:views 倒序，附 SPU 快照） */
    public List<Map<String, Object>> topViews(int topN) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(KEY_VIEWS_RANK, 0, topN - 1L);
        return fillSpuInfo(entries, false);
    }

    // ==================== 点赞（10.5） ====================

    /** 点赞（Set 天然幂等：重复点赞返回 false，不重复计数） */
    public boolean like(Long spuId, Long memberId) {
        if (spuMapper.selectById(spuId) == null) {
            throw new BizException("商品不存在");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().add(KEY_LIKE + spuId, String.valueOf(memberId)));
    }

    /** 取消点赞 */
    public void unlike(Long spuId, Long memberId) {
        redisTemplate.opsForSet().remove(KEY_LIKE + spuId, String.valueOf(memberId));
    }

    /** 点赞数（SCARD） */
    public long likeCount(Long spuId) {
        Long count = redisTemplate.opsForSet().size(KEY_LIKE + spuId);
        return count == null ? 0 : count;
    }

    /** 是否已点赞（SISMEMBER） */
    public boolean liked(Long spuId, Long memberId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KEY_LIKE + spuId, String.valueOf(memberId)));
    }

    // ==================== 浏览足迹（10.6） ====================

    /** 记录足迹：ZADD（score=时间戳）+ 截断保留最近 50 条 */
    public void recordHistory(Long memberId, Long spuId) {
        redisTemplate.opsForZSet().add(KEY_HISTORY + memberId, String.valueOf(spuId), System.currentTimeMillis());
        redisTemplate.opsForZSet().removeRange(KEY_HISTORY + memberId, 0, -(HISTORY_LIMIT + 1L));
    }

    /** 最近浏览（ZREVRANGE 按时间倒序，附 SPU 快照） */
    public List<Map<String, Object>> history(Long memberId) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(KEY_HISTORY + memberId, 0, HISTORY_LIMIT - 1L);
        return fillSpuInfo(entries, true);
    }

    // ==================== 内部 ====================

    /** ZSET 条目组装 SPU 快照（排行/足迹通用；rank 模式 member=spuId，history 模式 member=spuId 且带浏览时间） */
    private List<Map<String, Object>> fillSpuInfo(Set<ZSetOperations.TypedTuple<String>> entries, boolean withTime) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }
        List<Long> spuIds = new ArrayList<>();
        for (var entry : entries) {
            spuIds.add(Long.valueOf(entry.getValue()));
        }
        Map<Long, ProductSpu> spuMap = spuMapper.selectList(new LambdaQueryWrapper<ProductSpu>()
                        .in(ProductSpu::getId, spuIds))
                .stream().collect(Collectors.toMap(ProductSpu::getId, Function.identity()));
        for (var entry : entries) {
            Long spuId = Long.valueOf(entry.getValue());
            ProductSpu spu = spuMap.get(spuId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spuId", spuId);
            if (withTime) {
                row.put("viewTime", entry.getScore().longValue());
            } else {
                row.put("pv", entry.getScore().longValue());
            }
            if (spu != null) {
                row.put("spuName", spu.getName());
                row.put("mainPic", spu.getMainPic());
            }
            result.add(row);
        }
        return result;
    }
}
