package com.mall.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.product.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台评价管理：分页查询、商家回复、隐藏/显示（阶段 6 履约）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/api/admin/comment")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    /** 评价分页（支持商品名称/状态筛选） */
    @GetMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        return Result.success(commentService.adminPage(keyword, status, page, size));
    }

    /** 商家回复 */
    @PostMapping("/reply")
    public Result<Void> reply(@RequestParam Long id, @RequestParam String reply) {
        commentService.reply(id, reply);
        return Result.success();
    }

    /** 隐藏/显示（0隐藏 1正常） */
    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        commentService.updateStatus(id, status);
        return Result.success();
    }
}
