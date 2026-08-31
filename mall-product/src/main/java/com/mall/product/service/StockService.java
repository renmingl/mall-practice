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

    // ==================== 订单链路库存（阶段 5/6） ====================
    // 说明：扣减/回补/退货入库均写在 Seata 全局事务参与方（order 发起 @GlobalTransactional，
    // 扣减参与全局回滚）；退款回补/退货入库由 payment 经 MQ 投递触发（独立本地事务，必须幂等）

    /** 下单扣减库存（change_type=1）：SELECT FOR UPDATE 行锁串行化校验防超卖，
     *  兼容 Seata AT（主键等值定位，回滚按 undo log 恢复）。幂等由 order 侧 request_id 保证 */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(String bizSn, Long skuId, int quantity) {
        if (quantity <= 0) {
            throw new BizException("扣减数量必须大于 0");
        }
        // 行锁锁定该 SKU，串行化防超卖（并发下单同一 SKU 时排队校验）
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getId, skuId)
                .last("FOR UPDATE"));
        if (sku == null) {
            throw new BizException("SKU 不存在");
        }
        if (sku.getStock() < quantity) {
            throw new BizException("商品库存不足");
        }
        int before = sku.getStock();
        skuMapper.update(null, new UpdateWrapper<ProductSku>()
                .eq("id", skuId)
                .setSql("stock = stock - " + quantity)
                .setSql("sale_count = sale_count + " + quantity));
        insertLog(skuId, bizSn, 1, -quantity, before, before - quantity);
    }

    /** 回补库存（change_type：2取消回补 3退款回补 9秒杀回补）：MQ 至少一次投递，按
     *  bizSn+skuId+changeType 查重幂等（回补流水为正数，已回补则跳过；多 SKU 订单逐条回补
     *  时若缺 skuId 维度，第二条起会被首条流水误判为已回补而跳过） */
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(String bizSn, Long skuId, int quantity, int changeType) {
        if (quantity <= 0) {
            throw new BizException("回补数量必须大于 0");
        }
        Long exists = stockLogMapper.selectCount(new LambdaQueryWrapper<ProductStockLog>()
                .eq(ProductStockLog::getBizSn, bizSn)
                .eq(ProductStockLog::getSkuId, skuId)
                .eq(ProductStockLog::getChangeType, changeType)
                .gt(ProductStockLog::getChangeCount, 0));
        if (exists != null && exists > 0) {
            return;
        }
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

    /** 退货入库（change_type=6，退款退货确认收货联动）：按 refundSn+skuId 查重幂等（多 SKU 订单逐条入库） */
    @Transactional(rollbackFor = Exception.class)
    public void stockInReturn(String refundSn, Long skuId, int quantity) {
        Long exists = stockLogMapper.selectCount(new LambdaQueryWrapper<ProductStockLog>()
                .eq(ProductStockLog::getBizSn, refundSn)
                .eq(ProductStockLog::getSkuId, skuId)
                .eq(ProductStockLog::getChangeType, 6));
        if (exists != null && exists > 0) {
            return;
        }
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException("SKU 不存在");
        }
        int before = sku.getStock();
        skuMapper.update(null, new UpdateWrapper<ProductSku>()
                .eq("id", skuId)
                .setSql("stock = stock + " + quantity));
        insertLog(skuId, refundSn, 6, quantity, before, before + quantity);
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
