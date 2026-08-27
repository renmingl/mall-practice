package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.entity.ProductStockLog;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import com.mall.mbg.mapper.ProductStockLogMapper;
import com.mall.product.dto.StockCheckDTO;
import com.mall.product.util.ProductJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存服务：实时库存/流水查询、盘点调整（change_type=7）、入库加库存（change_type=5，供采购复用）
 * 场景 5.1/5.4/15.4；预警 5.5（low_stock 阈值，NULL 取全局默认）
 * @author renmingl
 * @date 2026-08-27 10:30:45
 */
@Service
@RequiredArgsConstructor
public class StockService {

    /** 全局默认预警阈值（sku.low_stock 为 NULL 时取此值） */
    public static final int DEFAULT_LOW_STOCK = 10;

    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductStockLogMapper stockLogMapper;

    /** 实时库存分页（附 SPU 名称，支持 skuCode / spu 名称筛选） */
    public Page<Map<String, Object>> page(String keyword, long page, long size) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<ProductSku>()
                .orderByDesc(ProductSku::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            // 先按 spu 编码/名称反查 spuId 列表，避免 SQL 拼接注入
            List<Long> spuIds = spuMapper.selectList(new LambdaQueryWrapper<ProductSpu>()
                            .like(ProductSpu::getSpuCode, keyword)
                            .or()
                            .like(ProductSpu::getName, keyword))
                    .stream().map(ProductSpu::getId).toList();
            // 注意：in 条件必须在 spuIds 非空时拼接，否则生成非法 SQL（spu_id IN ()）
            wrapper.and(w -> w.like(ProductSku::getSkuCode, keyword)
                    .or(!spuIds.isEmpty(), w2 -> w2.in(ProductSku::getSpuId, spuIds)));
        }
        Page<ProductSku> skuPage = skuMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> spuNames = new java.util.HashMap<>();
        List<Long> spuIds = skuPage.getRecords().stream().map(ProductSku::getSpuId).distinct().toList();
        if (!spuIds.isEmpty()) {
            for (ProductSpu spu : spuMapper.selectBatchIds(spuIds)) {
                spuNames.put(spu.getId(), spu.getName());
            }
        }
        List<Map<String, Object>> data = skuPage.getRecords().stream().map(sku -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sku.getId());
            row.put("skuCode", sku.getSkuCode());
            row.put("spuId", sku.getSpuId());
            row.put("spuName", spuNames.getOrDefault(sku.getSpuId(), ""));
            row.put("spec", ProductJsonUtil.unwrapText(sku.getSpec()));
            row.put("price", sku.getPrice());
            row.put("stock", sku.getStock());
            row.put("lowStock", sku.getLowStock());
            row.put("warning", sku.getStock() < (sku.getLowStock() == null ? DEFAULT_LOW_STOCK : sku.getLowStock()));
            row.put("status", sku.getStatus());
            row.put("updateTime", sku.getUpdateTime());
            return row;
        }).collect(Collectors.toList());
        Page<Map<String, Object>> result = new Page<>(skuPage.getCurrent(), skuPage.getSize(), skuPage.getTotal());
        result.setRecords(data);
        return result;
    }

    /** 库存流水分页（按 SKU 或全部） */
    public Page<ProductStockLog> logs(Long skuId, long page, long size) {
        return stockLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductStockLog>()
                        .eq(skuId != null, ProductStockLog::getSkuId, skuId)
                        .orderByDesc(ProductStockLog::getCreateTime));
    }

    /** 库存预警列表（stock < low_stock，low_stock NULL 取全局默认阈值） */
    public List<Map<String, Object>> warnings() {
        return skuMapper.selectList(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getStatus, 1))
                .stream()
                .filter(sku -> sku.getStock() < (sku.getLowStock() == null ? DEFAULT_LOW_STOCK : sku.getLowStock()))
                .map(sku -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", sku.getId());
                    row.put("skuCode", sku.getSkuCode());
                    row.put("spuId", sku.getSpuId());
                    row.put("stock", sku.getStock());
                    row.put("lowStock", sku.getLowStock() == null ? DEFAULT_LOW_STOCK : sku.getLowStock());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** 盘点调整（change_type=7）：差额记流水，库存用乐观锁防并发覆盖 */
    @Transactional(rollbackFor = Exception.class)
    public void check(StockCheckDTO dto) {
        if (dto.getSkuId() == null || dto.getStock() == null) {
            throw new BizException("SKU ID 和盘点数量必填");
        }
        ProductSku sku = skuMapper.selectById(dto.getSkuId());
        if (sku == null) {
            throw new BizException("SKU 不存在");
        }
        int diff = dto.getStock() - sku.getStock();
        if (diff == 0) {
            throw new BizException("盘点数量与当前库存一致，无需调整");
        }
        sku.setStock(dto.getStock());
        // @Version 乐观锁：并发盘点时 version 不一致则更新失败
        int rows = skuMapper.updateById(sku);
        if (rows == 0) {
            throw new BizException("库存已被其他操作修改，请刷新后重试");
        }
        insertLog(sku.getId(), dto.getRemark(), 7, diff, sku.getStock() - diff, sku.getStock());
    }

    /** 入库加库存（采购入库 change_type=5 / 退货入库 6 复用；setSql 原子自增避免并发覆盖） */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long skuId, int quantity, String bizSn, int changeType) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException("SKU 不存在");
        }
        int before = sku.getStock();
        skuMapper.update(null, new UpdateWrapper<ProductSku>()
                .eq("id", skuId)
                .setSql("stock = stock + " + quantity));
        insertLog(skuId, bizSn, changeType, quantity, before, before + quantity);
    }

    /** 记录库存流水（change_count 正数增加、负数减少，与表注释一致） */
    private void insertLog(Long skuId, String bizSn, int changeType, int changeCount, int before, int after) {
        ProductStockLog stockLog = new ProductStockLog();
        stockLog.setSkuId(skuId);
        stockLog.setBizSn(bizSn);
        stockLog.setChangeType((byte) changeType);
        stockLog.setChangeCount(changeCount);
        stockLog.setStockBefore(before);
        stockLog.setStockAfter(after);
        stockLogMapper.insert(stockLog);
    }
}
