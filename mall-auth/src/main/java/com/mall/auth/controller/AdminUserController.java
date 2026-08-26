package com.mall.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.service.AdminUserService;
import com.mall.common.result.Result;
import com.mall.mbg.entity.AdminRole;
import com.mall.mbg.entity.AdminUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * 后台用户管理接口（RBAC 权限粒度到按钮，1.9 @PreAuthorize 校验 perms）
 * @author renmingl
 * @date 2026-08-26 10:07:07
 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页列表 */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:user:list')")
    public Result<Page<AdminUser>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String username,
                                        @RequestParam(required = false) Integer status) {
        return Result.success(adminUserService.page(page, size, username, status));
    }

    /** 新增用户 */
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:user:add')")
    public Result<Void> add(@RequestBody AdminUser adminUser) {
        adminUserService.add(adminUser);
        return Result.success();
    }

    /** 修改用户（昵称/手机号/邮箱/状态） */
    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:user:update')")
    public Result<Void> update(@RequestBody AdminUser adminUser) {
        adminUserService.update(adminUser);
        return Result.success();
    }

    /** 删除用户（超级管理员不可删） */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:user:delete')")
    public Result<Void> delete(@PathVariable("id") Long id) {
        adminUserService.delete(id);
        return Result.success();
    }

    /** 重置密码 */
    @PutMapping("/{id}/password")
    @PreAuthorize("@ss.hasPerm('system:user:resetPwd')")
    public Result<Void> resetPassword(@PathVariable("id") Long id, @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.getNewPassword());
        return Result.success();
    }

    /** 用户已分配角色（回显） */
    @GetMapping("/{id}/roles")
    @PreAuthorize("@ss.hasPerm('system:user:assign')")
    public Result<List<AdminRole>> rolesOf(@PathVariable("id") Long id) {
        return Result.success(adminUserService.rolesOf(id));
    }

    /** 分配角色 */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@ss.hasPerm('system:user:assign')")
    public Result<Void> assignRoles(@PathVariable("id") Long id, @RequestBody AssignRolesRequest request) {
        adminUserService.assignRoles(id, request.getRoleIds());
        return Result.success();
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度 6-32 位")
        private String newPassword;
    }

    @Data
    public static class AssignRolesRequest {
        private List<Long> roleIds;
    }
}
