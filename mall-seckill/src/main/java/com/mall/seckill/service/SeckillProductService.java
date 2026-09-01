package com.mall.seckill.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.product.ProductFeignClient;
import com.mall.api.product.SkuOrderInfoDTO;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.SeckillProduct;
import com.mall.mbg.entity.SeckillSession;
import com.mall.mbg.mapper.SeckillProductMapper;
import com.mall.seckill.dto.SeckillProductSaveDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀商品配置服务（14.2）：后台 CRUD（校验 seckill_stock ≤ sku.stock）
 * 秒杀价须低于 SKU 原价；同一场次同一 SKU 唯一（uk_session_sku 兜底）
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillProductService {

    private final SeckillProductMapper productMapper;
    private final SeckillSessionService sessionService;
    private final ProductFeignClient productFeignClient;

    /** 后台分页（按场次/状态筛选） */
    public Page<Map<String, Object>> adminPage(Long sessionId, Integer status, long page, long size) {
        Page<SeckillProduct> productPage = productMapper.selectPage(new Page<>(page, size),
                Wrappers.<SeckillProduct>lambdaQuery()
                        .eq(sessionId != null, SeckillProduct::getSessionId, sessionId)
                        .eq(status != null, SeckillProduct::getStatus, status)
                        .orderByDesc(SeckillProduct::getCreateTime));
        Page<Map<String, Object>> result = new Page<>(productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(productPage.getRecords().stream().map(p -> toRow(p)).toList());
        return result;
    }

    /** 场次下启用商品列表（后台配置页展示用） */
    public List<Map<String, Object>> listBySession(Long sessionId) {
        return productMapper.selectList(Wrappers.<SeckillProduct>lambdaQuery()
                        .eq(SeckillProduct::getSessionId, sessionId)
                        .eq(SeckillProduct::getStatus, 1)
                        .orderByAsc(SeckillProduct::getId))
                .stream().map(this::toRow).toList();
    }

    /** 场次下启用商品实体列表（预热用，不组装快照） */
    public List<SeckillProduct> listEnabled(Long sessionId) {
        return productMapper.selectList(Wrappers.<SeckillProduct>lambdaQuery()
                .eq(SeckillProduct::getSessionId, sessionId)
                .eq(SeckillProduct::getStatus, 1)
                .orderByAsc(SeckillProduct::getId));
    }

    /** 保存秒杀商品配置（新增/修改；校验秒杀价低于原价、秒杀库存 ≤ SKU 库存） */
    @Transactional(rollbackFor = Exception.class)
    public void save(SeckillProductSaveDTO dto) {
        SeckillSession session = sessionService.getById(dto.getSessionId());
        if (!session.getStartTime().isAfter(java.time.LocalDateTime.now())) {
            throw new BizException("场次已开始，不允许再配置秒杀商品");
        }
        SkuOrderInfoDTO sku = productFeignClient.getSkuOrderInfo(dto.getSkuId()).getDataOrThrow();
        if (sku.getStatus() == null || sku.getStatus() != 1 || sku.getSpuStatus() == null || sku.getSpuStatus() != 1) {
            throw new BizException("该 SKU 已下架或停用，不可配置秒杀");
        }
        if (dto.getSeckillPrice().compareTo(sku.getPrice()) >= 0) {
            throw new BizException("秒杀价必须低于商品原价（原价 " + sku.getPrice() + "）");
        }
        if (dto.getSeckillStock() > sku.getStock()) {
            throw new BizException("秒杀库存不能超过 SKU 当前库存（剩余 " + sku.getStock() + "）");
        }
        SeckillProduct product = dto.getId() == null ? new SeckillProduct() : getById(dto.getId());
        product.setSessionId(dto.getSessionId());
        product.setSpuId(sku.getSpuId());
        product.setSkuId(dto.getSkuId());
        product.setSeckillPrice(dto.getSeckillPrice());
        product.setSeckillStock(dto.getSeckillStock());
        product.setLimitPerUser(dto.getLimitPerUser());
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        if (product.getId() == null) {
            product.setStatus(dto.getStatus() == null ? (byte) 1 : dto.getStatus());
            try {
                productMapper.insert(product);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BizException("该场次已配置过此 SKU");
            }
        } else {
            productMapper.updateById(product);
        }
        log.info("保存秒杀商品 id={} sessionId={} skuId={} price={} stock={} limit={}",
                product.getId(), product.getSessionId(), product.getSkuId(),
                product.getSeckillPrice(), product.getSeckillStock(), product.getLimitPerUser());
    }

    /** 启停秒杀商品 */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Byte status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态仅支持 0禁用 / 1启用");
        }
        SeckillProduct product = getById(id);
        product.setStatus(status);
        productMapper.updateById(product);
        log.info("切换秒杀商品状态 id={} status={}", id, status);
    }

    /** 删除秒杀商品配置（场次未开始才允许） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SeckillProduct product = getById(id);
        SeckillSession session = sessionService.getById(product.getSessionId());
        if (!session.getStartTime().isAfter(java.time.LocalDateTime.now())) {
            throw new BizException("场次已开始，不可删除秒杀商品");
        }
        productMapper.deleteById(id);
        log.info("删除秒杀商品 id={}", id);
    }

    /** 按 ID 查秒杀商品（不存在抛异常） */
    public SeckillProduct getById(Long id) {
        SeckillProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("秒杀商品不存在");
        }
        return product;
    }

    private Map<String, Object> toRow(SeckillProduct p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("sessionId", p.getSessionId());
        row.put("spuId", p.getSpuId());
        row.put("skuId", p.getSkuId());
        row.put("seckillPrice", p.getSeckillPrice());
        row.put("seckillStock", p.getSeckillStock());
        row.put("limitPerUser", p.getLimitPerUser());
        row.put("status", p.getStatus());
        try {
            SkuOrderInfoDTO sku = productFeignClient.getSkuOrderInfo(p.getSkuId()).getDataOrThrow();
            row.put("spuName", sku.getSpuName());
            row.put("skuCode", sku.getSkuCode());
            row.put("spec", sku.getSpec());
            row.put("pic", sku.getPic());
            row.put("price", sku.getPrice());
            row.put("skuStock", sku.getStock());
        } catch (Exception e) {
            row.put("spuName", "—");
        }
        return row;
    }
}
