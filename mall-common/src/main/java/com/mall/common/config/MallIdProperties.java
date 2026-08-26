package com.mall.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mall.id.* 配置：雪花 ID 生成器的节点参数（多实例部署时每实例分配不同 workerId）
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
@Data
@ConfigurationProperties(prefix = "mall.id")
public class MallIdProperties {

    /** 工作节点 ID（0~31），默认 0 */
    private long workerId = 0L;

    /** 数据中心 ID（0~31），默认 0 */
    private long datacenterId = 0L;
}
