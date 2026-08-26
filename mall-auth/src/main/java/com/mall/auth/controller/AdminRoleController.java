package com.mall.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.service.AdminRoleService;
import com.mall.common.result.Result;
import com.mall.mbg.entity.AdminRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台角色管理接口（RBAC 五表：角色 CRUD + 分配菜单权限）
 * @author renmingl
 * @date 2026-08-26 09:42:58
 */
@RestController
@RequestMapping("/api/admin/role")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /** 全部角色（下拉选择用） */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPerm('system:role:list')")
    public Result<List<AdminRole>> list() {
        return Result.success(adminRoleService.list());
    }

    /** 角色分页 */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:role:list')")
    public Result<Page<AdminRole>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String name) {
        return Result.success(adminRoleService.page(page, size, name));
    }

    /** 新增角色 */
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:role:add')")
    public Result<Void> add(@RequestBody AdminRole role) {
        adminRoleService.add(role);
        return Result.success();
    }

    /** 修改角色 */
    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:role:update')")
    public Result<Void> update(@RequestBody AdminRole role) {
        adminRoleService.update(role);
        return Result.success();
    }

    /** 删除角色（已分配用户不可删） */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:role:delete')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        adminRoleService.delete(id);
        return Result.success();
    }

    /** 角色已分配菜单（回显） */
    @GetMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('system:role:assign')")
    public Result<List<Long>> menuIdsOf(@PathVariable("id") Long id) {
        return Result.success(adminRoleService.menuIdsOf(id));
    }

    /** 分配菜单权限 */
    @PutMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('system:role:assign')")
    public Result<Void> assignMenus(@PathVariable("id") Long id, @RequestBody AssignMenusRequest request) {
        adminRoleService.assignMenus(id, request.getMenuIds());
        return Result.success();
    }

    @Data
    public static class AssignMenusRequest {
        private List<Long> menuIds;
    }
}
