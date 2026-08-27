package com.mall.product.service;

import com.mall.common.exception.BizException;
import com.mall.common.id.SnowflakeIdGenerator;
import com.mall.product.storage.UploadStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 图片上传服务（场景 2.6）：文件校验 + 存储通道调度
 * 调度规则：按 @Order 优先级选择第一个 enabled=true 的存储实现（OSS 配置启用则走 OSS，本地存储最低优先级兜底）
 * 接入其他对象存储（如 OBS）时，新增 UploadStorage 实现类并注册为 Bean 即可，无需改动本类
 * @author renmingl
 * @date 2026-08-27 20:15:25
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    /** 存储通道实现列表（按 @Order 排序注入，本地存储为最低优先级兜底） */
    private final List<UploadStorage> storages;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 上传图片：校验通过后交给启用的存储通道
     * @return 可访问 URL（本地=相对路径 /uploads/...；OSS=完整 http(s) URL）
     */
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("图片大小不能超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = original == null ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 jpg/jpeg/png/gif/webp 格式");
        }
        // 按日期分目录：20260827/雪花ID.jpg，本地与 OSS 同一命名规则
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = snowflakeIdGenerator.nextId() + "." + ext;
        UploadStorage storage = storages.stream()
                .filter(UploadStorage::enabled)
                .findFirst()
                .orElseThrow(() -> new BizException("未配置可用的存储通道"));
        try {
            return storage.upload(file, dateDir, fileName);
        } catch (IOException e) {
            throw new BizException("图片保存失败：" + e.getMessage());
        }
    }
}
