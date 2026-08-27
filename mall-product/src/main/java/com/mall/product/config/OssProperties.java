package com.mall.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（场景 2.6 图片上传）
 * enabled=false（默认）走本地存储；true 时 endpoint/accessKeyId/accessKeySecret/bucket 必填，启动 fail-fast 校验见 {@code OssUtil}
 * @author renmingl
 * @date 2026-08-27 20:15:00
 */
@Data
@ConfigurationProperties(prefix = "mall.product.oss")
public class OssProperties {

    /** 是否启用 OSS（不启用则默认本地文件存储） */
    private boolean enabled = false;

    /** 地域 Endpoint，例：oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    private String accessKeyId;

    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucket;

    /** 自定义访问域名（CDN/绑定域名，可选；为空则用 https://{bucket}.{endpoint} 拼接） */
    private String domain;

    /** 对象存储前缀目录 */
    private String dirPrefix = "product";
}
