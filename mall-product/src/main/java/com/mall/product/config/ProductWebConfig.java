package com.mall.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：/uploads/** → 本地上传目录（场景 2.6 图片上传后可直接访问）
 * @author renmingl
 * @date 2026-08-27 10:30:05
 */
@Configuration
public class ProductWebConfig implements WebMvcConfigurer {

    @Value("${mall.product.upload-dir:${user.home}/mall-uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
