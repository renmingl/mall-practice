package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductPurchase;
import com.mall.mbg.entity.ProductSupplier;
import com.mall.mbg.mapper.ProductPurchaseMapper;
import com.mall.mbg.mapper.ProductSupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 供应商服务（场景 15.1）：档案 CRUD + 停用
 * @author renmingl
 * @date 2026-08-27 10:30:50
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final ProductSupplierMapper supplierMapper;
    private final ProductPurchaseMapper purchaseMapper;

    public Page<ProductSupplier> page(String name, Integer status, long page, long size) {
        return supplierMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductSupplier>()
                        .like(StringUtils.hasText(name), ProductSupplier::getName, name)
                        .eq(status != null, ProductSupplier::getStatus, status)
                        .orderByDesc(ProductSupplier::getCreateTime));
    }

    public void add(ProductSupplier supplier) {
        supplier.setId(null);
        supplierMapper.insert(supplier);
    }

    public void update(ProductSupplier supplier) {
        if (supplier.getId() == null || supplierMapper.selectById(supplier.getId()) == null) {
            throw new BizException("供应商不存在");
        }
        supplierMapper.updateById(supplier);
    }

    /** 删除：有采购单关联不可删（可停用） */
    public void delete(Long id) {
        long count = purchaseMapper.selectCount(new LambdaQueryWrapper<ProductPurchase>()
                .eq(ProductPurchase::getSupplierId, id));
        if (count > 0) {
            throw new BizException("该供应商存在采购单，不可删除，可停用");
        }
        supplierMapper.deleteById(id);
    }

    /** 停用/启用：停用后不可新建采购单 */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("供应商状态仅支持 0停用 / 1启用");
        }
        ProductSupplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException("供应商不存在");
        }
        supplier.setStatus(status.byteValue());
        supplierMapper.updateById(supplier);
    }
}
