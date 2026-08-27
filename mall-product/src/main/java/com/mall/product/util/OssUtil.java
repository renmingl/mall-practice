package com.mall.product.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.mall.product.config.OssProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 工具类（场景 2.6 图片上传）
 * 客户端懒加载单例；启用时 fail-fast 校验必填配置；访问 URL 优先自定义域名（CDN），否则 bucket.endpoint
 * @author renmingl
 * @date 2026-08-27 20:15:05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssUtil {

    private final OssProperties properties;

    /** 客户端懒加载（double-checked locking，进程内单例，OSSClient 线程安全） */
    private volatile OSS client;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 启用 OSS 时校验必要配置，缺失直接启动失败（fail-fast，避免运行期才暴露） */
    @PostConstruct
    public void validate() {
        if (!isEnabled()) {
            return;
        }
        StringBuilder missing = new StringBuilder();
        if (!StringUtils.hasText(properties.getEndpoint())) {
            missing.append("endpoint、");
        }
        if (!StringUtils.hasText(properties.getAccessKeyId())) {
            missing.append("accessKeyId、");
        }
        if (!StringUtils.hasText(properties.getAccessKeySecret())) {
            missing.append("accessKeySecret、");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            missing.append("bucket、");
        }
        if (missing.length() > 0) {
            throw new IllegalStateException("OSS 已启用但缺少必要配置：mall.product.oss."
                    + missing.substring(0, missing.length() - 1));
        }
    }

    /**
     * 上传字节内容到 OSS，返回可访问 URL
     * @param content     文件字节
     * @param objectKey   对象 Key（含前缀目录）
     * @param contentType 内容类型（如 image/png）
     */
    public String upload(byte[] content, String objectKey, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(content.length);
        getClient().putObject(properties.getBucket(), objectKey,
                new ByteArrayInputStream(content), metadata);
        log.info("OSS 上传成功：bucket={}, key={}", properties.getBucket(), objectKey);
        return toUrl(objectKey);
    }

    /** 删除对象（如商品图替换时清理旧图） */
    public void delete(String objectKey) {
        getClient().deleteObject(properties.getBucket(), objectKey);
    }

    /** 拼接访问 URL：自定义域名（CDN）优先，否则 https://{bucket}.{endpoint}/{objectKey} */
    public String toUrl(String objectKey) {
        if (StringUtils.hasText(properties.getDomain())) {
            String domain = properties.getDomain().endsWith("/")
                    ? properties.getDomain().substring(0, properties.getDomain().length() - 1)
                    : properties.getDomain();
            return domain + "/" + objectKey;
        }
        return "https://" + properties.getBucket() + "." + properties.getEndpoint() + "/" + objectKey;
    }

    private OSS getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OSSClientBuilder().build(
                            properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
                    log.info("OSS 客户端初始化完成：endpoint={}", properties.getEndpoint());
                }
            }
        }
        return client;
    }
}
