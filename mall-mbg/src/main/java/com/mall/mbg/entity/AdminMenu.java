package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台菜单/权限表（RBAC 权限树）
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("admin_menu")
public class AdminMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单/权限ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 父菜单ID，0为顶级
     */
    private Long parentId;

    /**
     * 菜单/权限名称
     */
    private String name;

    /**
     * 类型：1目录 2菜单 3按钮
     */
    private Byte type;

    /**
     * 前端路由路径
     */
    private String path;

    /**
     * 权限标识（如 product:add，@PreAuthorize 校验用）
     */
    private String perms;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态：1启用 0禁用
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 子菜单（非表字段，权限树组装用）
     */
    @TableField(exist = false)
    private List<AdminMenu> children = new ArrayList<>();
}
