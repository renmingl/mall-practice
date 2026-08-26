package com.mall.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.util.JwtUtil;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.AdminRole;
import com.mall.mbg.entity.AdminRoleMenu;
import com.mall.mbg.entity.AdminUserRole;
import com.mall.mbg.mapper.AdminRoleMapper;
import com.mall.mbg.mapper.AdminRoleMenuMapper;
import com.mall.mbg.mapper.AdminUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台角色管理（RBAC）：分页 / 增删改 / 分配菜单权限
 * @author renmingl
 * @date 2026-08-26 22:56:19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AuthRedisService authRedisService;

    /** 全部角色（分配用下拉） */
    public List<AdminRole> list() {
        return adminRoleMapper.selectList(new LambdaQueryWrapper<AdminRole>()
                .orderByAsc(AdminRole::getId));
    }

    /** 分页查询 */
    public Page<AdminRole> page(long page, long size, String name) {
        return adminRoleMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminRole>()
                        .like(name != null, AdminRole::getName, name)
                        .orderByDesc(AdminRole::getCreateTime));
    }

    /** 新增：角色编码唯一 */
    public void add(AdminRole role) {
        Long count = adminRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getCode, role.getCode()));
        if (count > 0) {
            throw new BizException("角色编码已存在");
        }
        role.setId(null);
        role.setStatus(role.getStatus() == null ? (byte) 1 : role.getStatus());
        adminRoleMapper.insert(role);
        log.info("新增角色：code={}", role.getCode());
    }

    /** 修改（名称/描述/状态；编码不可改，避免权限语义漂移）；超级管理员角色不可禁用（防系统锁死） */
    public void update(AdminRole update) {
        AdminRole exist = adminRoleMapper.selectById(update.getId());
        if (exist == null) {
            throw new BizException("角色不存在");
        }
        if (AdminAuthService.ROLE_SUPER_ADMIN.equals(exist.getCode())
                && update.getStatus() != null && update.getStatus() != 1) {
            throw new BizException("超级管理员角色不可禁用");
        }
        if (update.getName() != null) {
            exist.setName(update.getName());
        }
        if (update.getDescription() != null) {
            exist.setDescription(update.getDescription());
        }
        if (update.getStatus() != null) {
            exist.setStatus(update.getStatus());
            if (update.getStatus() != 1) {
                // 角色被禁用：绑定该角色的用户权限即时失效，踢下线重新登录
                invalidateRoleUsers(update.getId());
            }
        }
        adminRoleMapper.updateById(exist);
    }

    /** 删除：已分配用户的角色不可删（保留审计完整性）；清理角色-菜单关联 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        Long userCount = adminUserRoleMapper.selectCount(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, roleId));
        if (userCount > 0) {
            throw new BizException("该角色已分配给用户，请先解除分配");
        }
        adminRoleMapper.deleteById(roleId);
        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>()
                .eq(AdminRoleMenu::getRoleId, roleId));
        log.info("删除角色：roleId={}", roleId);
    }

    /** 角色已分配菜单（回显） */
    public List<Long> menuIdsOf(Long roleId) {
        return adminRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getRoleId, roleId))
                .stream().map(AdminRoleMenu::getMenuId).toList();
    }

    /** 分配菜单权限：先清后插；权限变更后踢绑定用户下线，新权限即时生效 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        adminRoleMenuMapper.delete(new LambdaQueryWrapper<AdminRoleMenu>()
                .eq(AdminRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                AdminRoleMenu relation = new AdminRoleMenu();
                relation.setRoleId(roleId);
                relation.setMenuId(menuId);
                adminRoleMenuMapper.insert(relation);
            }
        }
        invalidateRoleUsers(roleId);
    }

    /** 踢下线：绑定该角色的全部用户（权限/状态变更即时生效） */
    private void invalidateRoleUsers(Long roleId) {
        List<Long> userIds = adminUserRoleMapper.selectList(
                        new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getRoleId, roleId))
                .stream().map(AdminUserRole::getUserId).distinct().toList();
        userIds.forEach(userId -> authRedisService.invalidateUserTokens(JwtUtil.USER_TYPE_ADMIN, userId));
    }
}
