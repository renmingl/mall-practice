package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.common.id.SnowflakeIdGenerator;
import com.mall.mbg.entity.ProductPurchase;
import com.mall.mbg.entity.ProductPurchaseItem;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSupplier;
import com.mall.mbg.mapper.ProductPurchaseItemMapper;
import com.mall.mbg.mapper.ProductPurchaseMapper;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSupplierMapper;
import com.mall.product.dto.PurchaseCreateDTO;
import com.mall.product.dto.PurchaseReceiveDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购单服务（场景 15.2/15.3）：状态机（0待审核 1待收货 2部分入库 3已完成 4已取消）+ 分批入库
 * 入库事务：明细已入库数累计 + sku.stock 增加 + stock_log 留痕（change_type=5）
 * @author renmingl
 * @date 2026-08-27 10:30:55
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final ProductPurchaseMapper purchaseMapper;
    private final ProductPurchaseItemMapper itemMapper;
    private final ProductSupplierMapper supplierMapper;
    private final ProductSkuMapper skuMapper;
    private final StockService stockService;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 创建采购单（状态 0 待审核；采购单号雪花 ID，幂等唯一键 uk_purchase_sn 兜底） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseCreateDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException("采购明细不能为空");
        }
        ProductSupplier supplier = supplierMapper.selectById(dto.getSupplierId());
        if (supplier == null || supplier.getStatus() == 0) {
            throw new BizException("供应商不存在或已停用");
        }
        ProductPurchase purchase = new ProductPurchase();
        purchase.setPurchaseSn(String.valueOf(snowflakeIdGenerator.nextId()));
        purchase.setSupplierId(dto.getSupplierId());
        BigDecimal total = BigDecimal.ZERO;
        // 先校验并收集明细（此时主单未入库，不能取 purchase.getId() 回填明细）
        List<ProductPurchaseItem> details = new java.util.ArrayList<>();
        java.util.Set<Long> skuIds = new java.util.HashSet<>();
        for (PurchaseCreateDTO.Item item : dto.getItems()) {
            ProductSku sku = skuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw new BizException("SKU 不存在：" + item.getSkuId());
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BizException("采购数量必须大于 0");
            }
            if (item.getPurchasePrice() == null || item.getPurchasePrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("采购单价必须大于 0");
            }
            // uk_purchase_sku（purchase_id + sku_id 唯一）：重复 SKU 给出友好提示，而非依赖唯一键报错
            if (!skuIds.add(item.getSkuId())) {
                throw new BizException("采购明细中 SKU 重复：" + item.getSkuId());
            }
            ProductPurchaseItem detail = new ProductPurchaseItem();
            detail.setSkuId(item.getSkuId());
            detail.setQuantity(item.getQuantity());
            detail.setReceivedQuantity(0);
            detail.setPurchasePrice(item.getPurchasePrice());
            details.add(detail);
            total = total.add(item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        // 先插主单拿自增 ID，再回填明细的 purchase_id
        purchase.setTotalAmount(total);
        purchase.setStatus((byte) 0);
        purchaseMapper.insert(purchase);
        for (ProductPurchaseItem detail : details) {
            detail.setPurchaseId(purchase.getId());
            itemMapper.insert(detail);
        }
        return purchase.getId();
    }

    public Page<ProductPurchase> page(Byte status, Long supplierId, long page, long size) {
        return purchaseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductPurchase>()
                        .eq(status != null, ProductPurchase::getStatus, status)
                        .eq(supplierId != null, ProductPurchase::getSupplierId, supplierId)
                        .orderByDesc(ProductPurchase::getCreateTime));
    }

    /** 采购单详情（主单 + 明细 + 供应商名） */
    public Map<String, Object> detail(Long id) {
        ProductPurchase purchase = purchaseMapper.selectById(id);
        if (purchase == null) {
            throw new BizException("采购单不存在");
        }
        List<ProductPurchaseItem> items = itemMapper.selectList(new LambdaQueryWrapper<ProductPurchaseItem>()
                .eq(ProductPurchaseItem::getPurchaseId, id));
        ProductSupplier supplier = supplierMapper.selectById(purchase.getSupplierId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("purchase", purchase);
        data.put("supplierName", supplier == null ? null : supplier.getName());
        data.put("items", items);
        return data;
    }

    /** 审核：通过 0→1 待收货；驳回按取消处理 0→4 */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String auditBy) {
        ProductPurchase purchase = getAndCheck(id, (byte) 0, "仅待审核状态可审核");
        purchase.setStatus(pass ? (byte) 1 : (byte) 4);
        purchase.setAuditBy(auditBy);
        purchase.setAuditTime(LocalDateTime.now());
        purchaseMapper.updateById(purchase);
    }

    /** 取消（待审核/待收货可取消；已有入库的不可取消） */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ProductPurchase purchase = purchaseMapper.selectById(id);
        if (purchase == null) {
            throw new BizException("采购单不存在");
        }
        if (purchase.getStatus() != 0 && purchase.getStatus() != 1) {
            throw new BizException("当前状态不可取消");
        }
        long received = itemMapper.selectList(new LambdaQueryWrapper<ProductPurchaseItem>()
                        .eq(ProductPurchaseItem::getPurchaseId, id)).stream()
                .mapToInt(ProductPurchaseItem::getReceivedQuantity).sum();
        if (received > 0) {
            throw new BizException("已有入库记录，不可取消");
        }
        purchase.setStatus((byte) 4);
        purchaseMapper.updateById(purchase);
    }

    /** 分批入库（明细级）：本次入库数量 ≤ 剩余可收数量，收满则采购单置已完成 */
    @Transactional(rollbackFor = Exception.class)
    public void receive(PurchaseReceiveDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new BizException("入库数量必须大于 0");
        }
        ProductPurchaseItem item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new BizException("采购明细不存在");
        }
        ProductPurchase purchase = purchaseMapper.selectById(item.getPurchaseId());
        if (purchase == null || purchase.getStatus() == 4) {
            throw new BizException("采购单已取消");
        }
        if (purchase.getStatus() != 1 && purchase.getStatus() != 2) {
            throw new BizException("采购单尚未审核通过，不能入库");
        }
        int remain = item.getQuantity() - item.getReceivedQuantity();
        if (dto.getQuantity() > remain) {
            throw new BizException("入库数量超过剩余可收数量（剩余 " + remain + "）");
        }
        // 条件原子更新：received_quantity + 本次数量 不得超过采购数量，防止并发入库超收
        int rows = itemMapper.update(null, new UpdateWrapper<ProductPurchaseItem>()
                .eq("id", item.getId())
                .apply("received_quantity + " + dto.getQuantity() + " <= quantity")
                .setSql("received_quantity = received_quantity + " + dto.getQuantity()));
        if (rows == 0) {
            throw new BizException("入库数量超过剩余可收数量，请刷新后重试");
        }
        // 库存 + 流水（change_type=5 采购入库），biz_sn 记采购单号可对账
        stockService.stockIn(item.getSkuId(), dto.getQuantity(), purchase.getPurchaseSn(), 5);
        // 采购单状态重算：全部收满 → 3 已完成；否则 → 2 部分入库
        boolean allReceived = itemMapper.selectList(new LambdaQueryWrapper<ProductPurchaseItem>()
                        .eq(ProductPurchaseItem::getPurchaseId, purchase.getId())).stream()
                .allMatch(i -> i.getReceivedQuantity().equals(i.getQuantity()));
        purchase.setStatus(allReceived ? (byte) 3 : (byte) 2);
        purchaseMapper.updateById(purchase);
    }

    private ProductPurchase getAndCheck(Long id, byte expectedStatus, String message) {
        ProductPurchase purchase = purchaseMapper.selectById(id);
        if (purchase == null || purchase.getStatus() != expectedStatus) {
            throw new BizException(message);
        }
        return purchase;
    }
}
