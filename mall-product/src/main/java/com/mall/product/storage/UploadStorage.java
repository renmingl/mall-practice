package com.mall.product.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片存储抽象（场景 2.6）：本地/OSS 双实现，接入其他对象存储（OBS 等）只需新增实现类
 * 调度规则：UploadService 按 @Order 优先级选择 enabled=true 的实现；本地存储设为最低优先级（Ordered.LOWEST_PRECEDENCE）仅兜底，
 * 其他云存储启用时自动优先
 * @author renmingl
 * @date 2026-08-27 20:15:10
 */
public interface UploadStorage {

    /** 该存储通道是否可用（可同时存在多个 enabled 实现，按注册顺序择优） */
    boolean enabled();

    /**
     * 上传图片
     * @param file     上传文件
     * @param dateDir  日期子目录（yyyyMMdd）
     * @param fileName 存储文件名（含扩展名）
     * @return 可访问 URL（本地=相对路径 /uploads/...；OSS=完整 http(s) URL）
     */
    String upload(MultipartFile file, String dateDir, String fileName) throws IOException;
}
