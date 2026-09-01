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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存服务：实时库存/流水查询、盘点调整（change_type=7）、入库加库存（change_type=5，供采购复用）
 * 场景 5.1/5.4/15.4；预警 5.5（low_stock 阈值，NULL 取全局默认）；
 * 场景 10.5 商品销量排行榜：下单/秒杀扣减成功累计 Redis ZSET（rank:sales，member=skuId）
 * @author renmingl
 * @date 2026-08-27 10:30:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    /** 全局默认预警阈值（sku.low_stock 为 NULL 时取此值） */
    public static final int DEFAULT_LOW_STOCK = 10;

    /** 商品销量排行榜 ZSET key（member=skuId，score=销量；10.5） */
    public static final String KEY_SALES_RANK = "rank:sales";

    /** 秒杀扣减流水类型（change_type=4，关单回补判断用） */
    public static final int CHANGE_TYPE_SECKILL = 4;

    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductStockLogMapper stockLogMapper;
    private final StringRedisTemplate redisTemplate;

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
        try {
            skuMapper.update(null, new UpdateWrapper<ProductSku>()
                    .eq("id", skuId)
                    .setSql("stock = stock + " + quantity));
            insertLog(skuId, bizSn, changeType, quantity, before, before + quantity);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底（uk_biz_sku_type）：重复入库流水（退货入库重试）标记回滚，幂等跳过
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("重复入库流水，幂等跳过 skuId={} bizSn={} changeType={}", skuId, bizSn, changeType);
        }
    }

    // ==================== 订单链路库存（阶段 5/6） ====================
    // 说明：扣减/回补/退货入库均写在 Seata 全局事务参与方（order 发起 @GlobalTransactional，
    // 扣减参与全局回滚）；退款回补/退货入库由 payment 经 MQ 投递触发（独立本地事务，必须幂等）

    /** 扣减库存（change_type：1 下单扣减 / 4 秒杀扣减）：SELECT FOR UPDATE 行锁串行化校验防超卖，
     *  兼容 Seata AT（主键等值定位，回滚按 undo log 恢复）。幂等由 order 侧 request_id 保证；
     *  扣减成功同步累计销量榜（Redis ZINCRBY，运营数据允许最终一致——全局回滚时不回滚 Redis） */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(String bizSn, Long skuId, int quantity, int changeType) {
        if (quantity <= 0) {
            throw new BizException("扣减数量必须大于 0");
        }
        if (changeType != 1 && changeType != CHANGE_TYPE_SECKILL) {
            throw new BizException("不支持的扣减类型");
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
        try {
            skuMapper.update(null, new UpdateWrapper<ProductSku>()
                    .eq("id", skuId)
                    .setSql("stock = stock - " + quantity)
                    .setSql("sale_count = sale_count + " + quantity));
            insertLog(skuId, bizSn, changeType, -quantity, before, before - quantity);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底（uk_biz_sku_type）：同一 bizSn+sku+类型重复扣减（重放）标记回滚，幂等跳过
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("重复扣减流水，幂等跳过 bizSn={} skuId={} changeType={}", bizSn, skuId, changeType);
            return;
        }
        // 10.5 销量榜：累计该 SKU 销量（member=skuId，score=销量）
        redisTemplate.opsForZSet().incrementScore(KEY_SALES_RANK, String.valueOf(skuId), quantity);
    }

    /** 商品销量排行榜（10.5）：ZSET rank:sales 按销量倒序取 Top N，附 SKU 快照 */
    public List<Map<String, Object>> salesRank(int topN) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(KEY_SALES_RANK, 0, topN - 1L);
        List<Map<String, Object>> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }
        List<Long> skuIds = entries.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .map(Long::valueOf)
                .toList();
        Map<Long, ProductSku> skuMap = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getId, skuIds))
                .stream().collect(Collectors.toMap(ProductSku::getId, Function.identity()));
        Map<Long, String> spuNames = new java.util.HashMap<>();
        if (!skuMap.isEmpty()) {
            List<Long> spuIds = skuMap.values().stream().map(ProductSku::getSpuId).distinct().toList();
            for (ProductSpu spu : spuMapper.selectBatchIds(spuIds)) {
                spuNames.put(spu.getId(), spu.getName());
            }
        }
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            Long skuId = Long.valueOf(entry.getValue());
            ProductSku sku = skuMap.get(skuId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuId", skuId);
            row.put("sales", entry.getScore().longValue());
            if (sku != null) {
                row.put("spuId", sku.getSpuId());
                row.put("spuName", spuNames.getOrDefault(sku.getSpuId(), ""));
                row.put("pic", sku.getPic());
                row.put("price", sku.getPrice());
            }
            result.add(row);
        }
        return result;
    }

    /** 是否存在指定扣减流水（秒杀关单回补判断：change_type=4 扣过才回补 sku.stock，防未扣先补虚增） */
    public boolean hasSeckillDeducted(String bizSn, Long skuId) {
        Long count = stockLogMapper.selectCount(new LambdaQueryWrapper<ProductStockLog>()
                .eq(ProductStockLog::getBizSn, bizSn)
                .eq(ProductStockLog::getSkuId, skuId)
                .eq(ProductStockLog::getChangeType, CHANGE_TYPE_SECKILL)
                .lt(ProductStockLog::getChangeCount, 0));
        return count != null && count > 0;
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
        try {
            skuMapper.update(null, new UpdateWrapper<ProductSku>()
                    .eq("id", skuId)
                    .setSql("stock = stock + " + quantity));
            insertLog(skuId, bizSn, changeType, quantity, before, before + quantity);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底（uk_biz_sku_type）：并发下查重与插入存在竞态，重复回补标记回滚，幂等跳过
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("重复回补流水，幂等跳过 bizSn={} skuId={} changeType={}", bizSn, skuId, changeType);
        }
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
        try {
            skuMapper.update(null, new UpdateWrapper<ProductSku>()
                    .eq("id", skuId)
                    .setSql("stock = stock + " + quantity));
            insertLog(skuId, refundSn, 6, quantity, before, before + quantity);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底（uk_biz_sku_type）：并发下查重与插入存在竞态，重复入库标记回滚，幂等跳过
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.warn("重复退货入库流水，幂等跳过 refundSn={} skuId={}", refundSn, skuId);
        }
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
