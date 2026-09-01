package com.mall.seckill.constant;

/**
 * 秒杀常量：Redis Key 约定 + Lua 脚本
 * 库存链路：预热 SET seckill:stock:{pid}（活动开始前）→ Lua 原子扣减 + 限购 → MQ 削峰异步下单
 * → order 核验预扣资格（seckill:reserved）→ 建单扣 sku.stock（change_type=4）→ 结果写 seckill:result
 * 关单回补：活动进行中回补 Redis 秒杀库存；活动已结束回补 sku.stock（change_type=9）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
public final class SeckillConstants {

    private SeckillConstants() {
    }

    /** 场次信息缓存（String JSON，预热/列表读） */
    public static final String KEY_SESSION = "seckill:session:";

    /** 秒杀库存（String 剩余可抢数，预热后存在） */
    public static final String KEY_STOCK = "seckill:stock:";

    /** 限购计数（String，{pid}:{memberId} → 已购数量，活动期间有效） */
    public static final String KEY_LIMIT = "seckill:limit:";

    /** 预扣资格标记（String "1"，{pid}:{memberId}，Lua 扣减成功写入，order 落单前核验） */
    public static final String KEY_RESERVED = "seckill:reserved:";

    /** 下单结果（String JSON {status,orderSn,reason}，前端轮询；{memberId}:{pid}） */
    public static final String KEY_RESULT = "seckill:result:";

    /** 场次商品列表缓存（String JSON 数组，预热时写入） */
    public static final String KEY_PRODUCTS = "seckill:products:";

    /** 秒杀排行榜（ZSET member=skuId score=成交数，下单成功 ZINCRBY，10.4） */
    public static final String KEY_RANK = "seckill:rank:";

    /** 关单已回补标记（String "1"，key=orderSn，幂等防重复回补） */
    public static final String KEY_RELEASED = "seckill:released:";

    /** 接口防刷计数（String，{memberId} 固定窗口内请求次数，12.2） */
    public static final String KEY_ANTISPAM = "seckill:antispam:";

    /** 幂等 token（String，领取后提交时校验删除，12.3） */
    public static final String KEY_TOKEN = "seckill:token:";

    /** 预热提前量（分钟）：活动开始前 10 分钟自动预热 */
    public static final long PREHEAT_AHEAD_MINUTES = 10;

    /** 结果查询有效期（分钟）：下单结果保留 30 分钟供轮询 */
    public static final long RESULT_TTL_MINUTES = 30;

    /** 防刷窗口与阈值：1 秒内最多 3 次秒杀提交（同一用户） */
    public static final long ANTISPAM_SECONDS = 1;
    public static final int ANTISPAM_MAX = 3;

    /**
     * Lua：原子扣减秒杀库存 + 限购计数 + 写预扣资格（防超卖 / 防黄牛，9.6 / 14.4）
     * KEYS[1]=stock key  KEYS[2]=limit key  KEYS[3]=reserved key
     * ARGV[1]=本次数量  ARGV[2]=每人限购
     * 返回：1 成功；0 库存不足；-1 超过限购；-2 未预热/已结束
     */
    public static final String LUA_DEDUCT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -2 end
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bought + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return -1 end
            if stock < tonumber(ARGV[1]) then return 0 end
            redis.call('DECRBY', KEYS[1], ARGV[1])
            redis.call('INCRBY', KEYS[2], ARGV[1])
            redis.call('SET', KEYS[3], '1')
            return 1
            """;

    /**
     * Lua：提交秒杀时校验并删除幂等 token（12.3 防重复提交；不存在返回 0）
     * KEYS[1]=token key  ARGV[1]=token 值
     */
    public static final String LUA_CONSUME_TOKEN = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            return 0
            """;

    /** 扣减 Lua 脚本对象（服务启动时注册，execute 时提交脚本） */
    public static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> LUA_DEDUCT_SCRIPT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(LUA_DEDUCT, Long.class);

    /** 消费 token Lua 脚本对象 */
    public static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> LUA_CONSUME_TOKEN_SCRIPT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(LUA_CONSUME_TOKEN, Long.class);
}
