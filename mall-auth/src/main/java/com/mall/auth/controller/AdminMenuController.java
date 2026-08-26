package com.mall.auth.controller;

import com.mall.auth.service.AdminMenuService;
import com.mall.common.result.Result;
import com.mall.mbg.entity.AdminMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台菜单/权限管理接口（RBAC 权限树：树查询 / 增删改，perms 为按钮级权限标识）
 * @author renmingl
 * @date 2026-08-26 21:39:14
 */
@RestController
@RequestMapping("/api/admin/menu")
@RequiredArgsConstructor
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    /** 权限树（目录 → 菜单 → 按钮，前端动态路由/菜单渲染用） */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasPerm('system:menu:list')")
    public Result<List<AdminMenu>> tree() {
        return Result.success(adminMenuService.tree());
    }

    /** 全部菜单（权限树组件回显/勾选用，扁平） */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPerm('system:menu:list')")
    public Result<List<AdminMenu>> list() {
        return Result.success(adminMenuService.list());
    }

    /** 新增菜单/按钮 */
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:menu:add')")
    public Result<Void> add(@RequestBody AdminMenu menu) {
        adminMenuService.add(menu);
        return Result.success();
    }

    /** 修改菜单/按钮 */
    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:menu:update')")
    public Result<Void> update(@RequestBody AdminMenu menu) {
        adminMenuService.update(menu);
        return Result.success();
    }

    /** 删除菜单（有子节点或已分配角色时拒绝） */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:menu:delete')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        adminMenuService.delete(id);
        return Result.success();
    }
}
