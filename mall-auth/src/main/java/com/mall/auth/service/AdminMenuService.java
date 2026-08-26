package com.mall.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.AdminMenu;
import com.mall.mbg.entity.AdminRoleMenu;
import com.mall.mbg.mapper.AdminMenuMapper;
import com.mall.mbg.mapper.AdminRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台菜单/权限管理（RBAC 权限树）：树查询 / 增删改
 * type：1目录 2菜单 3按钮；perms 为按钮级权限标识（@PreAuthorize 校验用）
 * @author renmingl
 * @date 2026-08-26 19:01:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMenuService {

    private final AdminMenuMapper adminMenuMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;

    /** 权限树（按 parentId 组装，sort 升序） */
    public List<AdminMenu> tree() {
        List<AdminMenu> all = adminMenuMapper.selectList(new LambdaQueryWrapper<AdminMenu>()
                .orderByAsc(AdminMenu::getSort));
        List<AdminMenu> roots = new ArrayList<>();
        for (AdminMenu menu : all) {
            if (menu.getParentId() == null || menu.getParentId() == 0) {
                roots.add(buildNode(menu, all));
            }
        }
        return roots;
    }

    /** 全部菜单（权限树组件回显/勾选用，扁平） */
    public List<AdminMenu> list() {
        return adminMenuMapper.selectList(new LambdaQueryWrapper<AdminMenu>()
                .orderByAsc(AdminMenu::getSort));
    }

    /** 新增菜单/按钮 */
    public void add(AdminMenu menu) {
        menu.setId(null);
        menu.setParentId(menu.getParentId() == null ? 0L : menu.getParentId());
        menu.setStatus(menu.getStatus() == null ? (byte) 1 : menu.getStatus());
        adminMenuMapper.insert(menu);
    }

    /** 修改菜单/按钮 */
    public void update(AdminMenu update) {
        if (adminMenuMapper.selectById(update.getId()) == null) {
            throw new BizException("菜单不存在");
        }
        adminMenuMapper.updateById(update);
    }

    /** 删除：有子节点或已分配角色时拒绝（避免孤儿数据/权限丢失） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long menuId) {
        Long childCount = adminMenuMapper.selectCount(
                new LambdaQueryWrapper<AdminMenu>().eq(AdminMenu::getParentId, menuId));
        if (childCount > 0) {
            throw new BizException("请先删除子菜单");
        }
        Long roleCount = adminRoleMenuMapper.selectCount(
                new LambdaQueryWrapper<AdminRoleMenu>().eq(AdminRoleMenu::getMenuId, menuId));
        if (roleCount > 0) {
            throw new BizException("该菜单已分配给角色，请先解除分配");
        }
        adminMenuMapper.deleteById(menuId);
        log.info("删除菜单：menuId={}", menuId);
    }

    private AdminMenu buildNode(AdminMenu menu, List<AdminMenu> all) {
        for (AdminMenu child : all) {
            if (menu.getId().equals(child.getParentId())) {
                menu.getChildren().add(buildNode(child, all));
            }
        }
        return menu;
    }
}
