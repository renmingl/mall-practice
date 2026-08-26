package com.mall.common.id;

import com.mall.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法 ID 生成器（场景 12.10）
 * 结构：1bit 符号位 + 41bit 毫秒时间戳 + 5bit 数据中心 + 5bit 工作节点 + 12bit 毫秒内序列。
 * 时间回拨处理（对应订单号场景要求「回拨等待 / 备用生成器」）：
 * 回拨 ≤ {@link #MAX_BACKWARD_MS}（5ms）：自旋等待时钟追平，序列号继续递增
 * 回拨 &gt; 5ms：时钟异常，抛出 {@link BizException} 拒绝生成（学习项目采用失败快速暴露，不做备用生成器）
 * 使用：注入 Spring Bean（workerId/datacenterId 由配置 mall.id.worker-id / mall.id.datacenter-id 指定），
 * 或直接调用 {@link #getInstance()} 单例。
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Slf4j
public class SnowflakeIdGenerator {

    /** 最大容忍回拨（毫秒）：5ms 内自旋等待 */
    private static final long MAX_BACKWARD_MS = 5L;

    private static final long START_EPOCH = 1735689600000L; // 2025-01-01 00:00:00

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 默认单例（workerId=0, datacenterId=0），非 Spring 环境可直接使用 */
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(0, 0);

    private final long workerId;
    private final long datacenterId;

    /** 上次生成 ID 的时间戳（毫秒） */
    private long lastTimestamp = -1L;
    /** 毫秒内序列（0~4095） */
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 超出范围 0~" + MAX_WORKER_ID);
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId 超出范围 0~" + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public static SnowflakeIdGenerator getInstance() {
        return INSTANCE;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= MAX_BACKWARD_MS) {
                // 小回拨：等待时钟追平（最多 5ms），期间复用上次时间戳继续生成
                try {
                    Thread.sleep(offset + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("ID 生成被中断");
                }
                timestamp = System.currentTimeMillis();
            } else {
                // 大回拨：时钟异常，拒绝生成避免重复 ID
                throw new BizException("系统时钟回拨 " + offset + "ms，拒绝生成 ID");
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 毫秒内序列耗尽，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - START_EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
