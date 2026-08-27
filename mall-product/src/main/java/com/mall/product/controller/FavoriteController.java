package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.product.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台商品收藏（场景 2.7）：收藏/取消/列表/状态，经网关鉴权后透传 X-User-Id
 * @author renmingl
 * @date 2026-08-27 10:31:45
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    /** 网关鉴权后透传的当前用户 ID 请求头（与 mall-gateway AuthGlobalFilter 保持一致） */
    public static final String HEADER_USER_ID = "X-User-Id";

    private final FavoriteService favoriteService;

    @PostMapping("/{spuId}")
    public Result<Void> add(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        favoriteService.add(memberId, spuId);
        return Result.success();
    }

    @DeleteMapping("/{spuId}")
    public Result<Void> remove(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        favoriteService.remove(memberId, spuId);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(@RequestHeader(HEADER_USER_ID) Long memberId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(favoriteService.list(memberId, page, size));
    }

    @GetMapping("/status/{spuId}")
    public Result<Boolean> status(@RequestHeader(HEADER_USER_ID) Long memberId, @PathVariable Long spuId) {
        return Result.success(favoriteService.isFavorite(memberId, spuId));
    }
}
