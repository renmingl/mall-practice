package com.mall.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.security.SecurityService;
import com.mall.auth.util.JwtUtil;
import com.mall.common.exception.BizException;
import com.mall.common.util.PasswordUtil;
import com.mall.mbg.entity.AdminRole;
import com.mall.mbg.entity.AdminUser;
import com.mall.mbg.entity.AdminUserRole;
import com.mall.mbg.mapper.AdminRoleMapper;
import com.mall.mbg.mapper.AdminUserMapper;
import com.mall.mbg.mapper.AdminUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台用户管理（1.7 后台账号增删改）：分页 / 新增 / 修改 / 删除 / 重置密码 / 分配角色
 * @author renmingl
 * @date 2026-08-26 11:06:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AuthRedisService authRedisService;
    private final SecurityService securityService;

    /** 分页查询（username 模糊 + 状态过滤） */
    public Page<AdminUser> page(long page, long size, String username, Integer status) {
        return adminUserMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminUser>()
                        .like(StringUtils.hasText(username), AdminUser::getUsername, username)
                        .eq(status != null, AdminUser::getStatus, status)
                        .orderByDesc(AdminUser::getCreateTime));
    }

    /** 新增：用户名唯一 + BCrypt 加密 */
    public void add(AdminUser adminUser) {
        if (!StringUtils.hasText(adminUser.getUsername()) || !StringUtils.hasText(adminUser.getPassword())) {
            throw new BizException("用户名和密码不能为空");
        }
        Long count = adminUserMapper.selectCount(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, adminUser.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        adminUser.setId(null);
        adminUser.setPassword(PasswordUtil.encode(adminUser.getPassword()));
        adminUser.setStatus(adminUser.getStatus() == null ? (byte) 1 : adminUser.getStatus());
        adminUserMapper.insert(adminUser);
        log.info("新增后台用户：username={}", adminUser.getUsername());
    }

    /** 修改基本信息（昵称/手机号/邮箱/状态；用户名与密码不可经此修改）；禁用时踢下线（旧令牌立即失效） */
    public void update(AdminUser update) {
        if (update.getId() == null) {
            throw new BizException("用户 ID 不能为空");
        }
        AdminUser exist = adminUserMapper.selectById(update.getId());
        if (exist == null) {
            throw new BizException("用户不存在");
        }
        if (StringUtils.hasText(update.getNickname())) {
            exist.setNickname(update.getNickname());
        }
        if (StringUtils.hasText(update.getPhone())) {
            exist.setPhone(update.getPhone());
        }
        if (StringUtils.hasText(update.getEmail())) {
            exist.setEmail(update.getEmail());
        }
        if (update.getStatus() != null) {
            exist.setStatus(update.getStatus());
            if (update.getStatus() != 1) {
                invalidate(update.getId());
            }
        }
        adminUserMapper.updateById(exist);
    }

    /** 删除：绑定超级管理员角色的用户不可删（避免误删唯一入口）；不允许删除自己 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long adminId) {
        if (hasRole(adminId, AdminAuthService.ROLE_SUPER_ADMIN)) {
            throw new BizException("超级管理员账号不可删除");
        }
        Long currentUserId = securityService.currentUserId();
        if (currentUserId != null && currentUserId.equals(adminId)) {
            throw new BizException("不能删除当前登录账号");
        }
        adminUserMapper.deleteById(adminId);
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>()
                .eq(AdminUserRole::getUserId, adminId));
        invalidate(adminId);
        log.info("删除后台用户：adminId={}", adminId);
    }

    /** 重置密码（BCrypt 加密覆盖）；重置后旧令牌全部失效，需重新登录 */
    public void resetPassword(Long adminId, String newPassword) {
        AdminUser exist = adminUserMapper.selectById(adminId);
        if (exist == null) {
            throw new BizException("用户不存在");
        }
        exist.setPassword(PasswordUtil.encode(newPassword));
        adminUserMapper.updateById(exist);
        invalidate(adminId);
    }

    /** 分配角色：先清后插（uk_user_role 兜底）；角色变更踢下线，权限立即重新生效 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long adminId, List<Long> roleIds) {
        adminUserRoleMapper.delete(new LambdaQueryWrapper<AdminUserRole>()
                .eq(AdminUserRole::getUserId, adminId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                AdminUserRole relation = new AdminUserRole();
                relation.setUserId(adminId);
                relation.setRoleId(roleId);
                adminUserRoleMapper.insert(relation);
            }
        }
        invalidate(adminId);
    }

    /** 用户角色列表（回显分配用） */
    public List<AdminRole> rolesOf(Long adminId) {
        List<Long> roleIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId))
                .stream().map(AdminUserRole::getRoleId).toList();
        return roleIds.isEmpty() ? List.of() : adminRoleMapper.selectBatchIds(roleIds);
    }

    private boolean hasRole(Long adminId, String roleCode) {
        List<Long> roleIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminId))
                .stream().map(AdminUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return adminRoleMapper.selectBatchIds(roleIds).stream()
                .anyMatch(role -> roleCode.equals(role.getCode()));
    }

    /** 失效该管理员全部令牌（踢下线），管理动作后权限/状态即时生效 */
    private void invalidate(Long adminId) {
        authRedisService.invalidateUserTokens(JwtUtil.USER_TYPE_ADMIN, adminId);
    }
}
