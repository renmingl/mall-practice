package com.mall.product.controller;

import com.mall.common.result.Result;
import com.mall.product.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传（场景 2.6）：本地文件存储为默认通道，配置 mall.product.oss.enabled=true 后自动切换阿里云 OSS
 * @author renmingl
 * @date 2026-08-27 20:15:30
 */
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    private final UploadService uploadService;

    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(uploadService.uploadImage(file));
    }
}
