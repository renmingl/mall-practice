package com.mall.product.storage;

import com.mall.product.config.OssProperties;
import com.mall.product.util.OssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 阿里云 OSS 存储实现（场景 2.6）：配置 mall.product.oss.enabled=true 后启用，上传返回完整 OSS/CDN URL
 * @author renmingl
 * @date 2026-08-27 20:15:20
 */
@Component
@RequiredArgsConstructor
public class OssUploadStorage implements UploadStorage {

    private final OssUtil ossUtil;
    private final OssProperties properties;

    @Override
    public boolean enabled() {
        return ossUtil.isEnabled();
    }

    @Override
    public String upload(MultipartFile file, String dateDir, String fileName) throws IOException {
        // 对象 Key：前缀目录/日期/文件名（雪花ID.扩展名）
        String objectKey = properties.getDirPrefix() + "/" + dateDir + "/" + fileName;
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return ossUtil.upload(file.getBytes(), objectKey, contentType);
    }
}
