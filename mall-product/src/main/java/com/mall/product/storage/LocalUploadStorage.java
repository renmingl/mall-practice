package com.mall.product.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现（场景 2.6 默认兜底通道，OSS 未启用时使用）
 * 存储目录取绝对路径，避免 Tomcat 运行时 user.dir 漂移到临时 work 目录；静态访问由 {@code ProductWebConfig} 映射 /uploads/**
 * @Order 最低优先级：其他存储通道（OSS 等）启用时优先，本地仅兜底
 * @author renmingl
 * @date 2026-08-27 20:15:15
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class LocalUploadStorage implements UploadStorage {

    @Value("${mall.product.upload-dir:${user.home}/mall-uploads}")
    private String uploadDir;

    @Override
    public boolean enabled() {
        // 本地存储始终可用，作为默认兜底通道
        return true;
    }

    @Override
    public String upload(MultipartFile file, String dateDir, String fileName) throws IOException {
        Path dir = Paths.get(uploadDir, dateDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(fileName).toFile());
        return "/uploads/" + dateDir + "/" + fileName;
    }
}
