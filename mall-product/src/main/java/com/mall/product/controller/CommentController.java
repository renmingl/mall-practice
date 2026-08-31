package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.product.dto.CommentCreateDTO;
import com.mall.product.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台商品评价（阶段 6 履约）：收货后评价（order 校验订单项）、商品评价列表、我的评价
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 发表评价（订单已完成 + 未评价才可；orderItemId 唯一防重） */
    @PostMapping
    public Result<Void> create(@RequestHeader("X-User-Id") Long memberId,
                               @Valid @RequestBody CommentCreateDTO dto) {
        commentService.create(memberId, dto);
        return Result.success();
    }

    /** 商品评价列表（仅正常状态） */
    @GetMapping("/spu/{spuId}")
    public Result<Page<Map<String, Object>>> listBySpu(@PathVariable Long spuId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.pageBySpu(spuId, page, size));
    }

    /** 我的评价 */
    @GetMapping("/mine")
    public Result<Page<Map<String, Object>>> mine(@RequestHeader("X-User-Id") Long memberId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.pageMine(memberId, page, size));
    }
}
