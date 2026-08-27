package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductCategory;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductCategoryMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品分类服务：分类树维护（场景 2.1，三级分类树：1一级 2二级 3三级）
 * @author renmingl
 * @date 2026-08-27 10:30:30
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryMapper categoryMapper;
    private final ProductSpuMapper spuMapper;

    /** 全量分类树（后台管理用，含禁用） */
    public List<Map<String, Object>> tree() {
        return buildTree(categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getSort, ProductCategory::getId)));
    }

    /** 前台分类树（仅启用） */
    public List<Map<String, Object>> enabledTree() {
        return buildTree(categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getStatus, 1)
                .orderByAsc(ProductCategory::getSort, ProductCategory::getId)));
    }

    private List<Map<String, Object>> buildTree(List<ProductCategory> all) {
        Map<Long, List<ProductCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(ProductCategory::getParentId));
        return buildChildren(0L, byParent);
    }

    private List<Map<String, Object>> buildChildren(Long parentId, Map<Long, List<ProductCategory>> byParent) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (ProductCategory c : byParent.getOrDefault(parentId, List.of())) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", c.getId());
            node.put("parentId", c.getParentId());
            node.put("name", c.getName());
            node.put("level", c.getLevel());
            node.put("icon", c.getIcon());
            node.put("sort", c.getSort());
            node.put("status", c.getStatus());
            node.put("children", buildChildren(c.getId(), byParent));
            nodes.add(node);
        }
        return nodes;
    }

    /** 新增（自动计算层级：父级 level + 1，最多三级） */
    public void add(ProductCategory category) {
        long parentId = category.getParentId() == null ? 0L : category.getParentId();
        category.setParentId(parentId);
        if (parentId == 0L) {
            category.setLevel((byte) 1);
        } else {
            ProductCategory parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException("父分类不存在");
            }
            if (parent.getLevel() >= 3) {
                throw new BizException("分类最多支持三级");
            }
            category.setLevel((byte) (parent.getLevel() + 1));
        }
        category.setId(null);
        categoryMapper.insert(category);
    }

    /** 修改（仅允许改名称/图标/排序/状态；变更父级时校验层级与循环引用） */
    public void update(ProductCategory category) {
        ProductCategory exist = categoryMapper.selectById(category.getId());
        if (category.getId() == null || exist == null) {
            throw new BizException("分类不存在");
        }
        long newParentId = category.getParentId() == null ? 0L : category.getParentId();
        if (newParentId != exist.getParentId()) {
            category.setParentId(newParentId);
            long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                    .eq(ProductCategory::getParentId, category.getId()));
            if (childCount > 0) {
                throw new BizException("该分类存在子分类，不可变更父级");
            }
            if (newParentId == category.getId()) {
                throw new BizException("父分类不能是自己");
            }
            if (newParentId == 0L) {
                category.setLevel((byte) 1);
            } else {
                ProductCategory parent = categoryMapper.selectById(newParentId);
                if (parent == null || parent.getStatus() == 0) {
                    throw new BizException("父分类不存在或已停用");
                }
                if (parent.getLevel() >= 3) {
                    throw new BizException("分类最多支持三级");
                }
                category.setLevel((byte) (parent.getLevel() + 1));
            }
        }
        categoryMapper.updateById(category);
    }

    /** 删除：有子分类或已挂商品不可删 */
    public void delete(Long id) {
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, id));
        if (childCount > 0) {
            throw new BizException("请先删除子分类");
        }
        long spuCount = spuMapper.selectCount(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getCategoryId, id));
        if (spuCount > 0) {
            throw new BizException("该分类下存在商品，不可删除");
        }
        categoryMapper.deleteById(id);
    }

    /** 启停 */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("分类状态仅支持 0禁用 / 1启用");
        }
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException("分类不存在");
        }
        category.setStatus(status.byteValue());
        categoryMapper.updateById(category);
    }
}
