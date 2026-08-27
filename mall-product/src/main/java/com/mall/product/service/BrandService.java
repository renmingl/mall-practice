package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductBrand;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductBrandMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 品牌服务（场景 2.1）：分页 CRUD + 启停
 * @author renmingl
 * @date 2026-08-27 10:30:35
 */
@Service
@RequiredArgsConstructor
public class BrandService {

    private final ProductBrandMapper brandMapper;
    private final ProductSpuMapper spuMapper;

    public Page<ProductBrand> page(String name, Integer status, long page, long size) {
        return brandMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductBrand>()
                        .like(StringUtils.hasText(name), ProductBrand::getName, name)
                        .eq(status != null, ProductBrand::getStatus, status)
                        .orderByAsc(ProductBrand::getSort, ProductBrand::getId));
    }

    /** 前台启用品牌列表（筛选下拉用） */
    public List<ProductBrand> enabledList() {
        return brandMapper.selectList(new LambdaQueryWrapper<ProductBrand>()
                .eq(ProductBrand::getStatus, 1)
                .orderByAsc(ProductBrand::getSort, ProductBrand::getId));
    }

    public void add(ProductBrand brand) {
        brand.setId(null);
        brandMapper.insert(brand);
    }

    public void update(ProductBrand brand) {
        if (brand.getId() == null || brandMapper.selectById(brand.getId()) == null) {
            throw new BizException("品牌不存在");
        }
        brandMapper.updateById(brand);
    }

    /** 删除：有商品关联不可删（可停用） */
    public void delete(Long id) {
        long spuCount = spuMapper.selectCount(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getBrandId, id));
        if (spuCount > 0) {
            throw new BizException("该品牌下存在商品，不可删除，可停用");
        }
        brandMapper.deleteById(id);
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("品牌状态仅支持 0禁用 / 1启用");
        }
        ProductBrand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BizException("品牌不存在");
        }
        brand.setStatus(status.byteValue());
        brandMapper.updateById(brand);
    }
}
