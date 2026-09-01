package com.mall.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.api.product.DeductStockDTO;
import com.mall.api.product.ReleaseStockDTO;
import com.mall.api.product.SkuOrderInfoDTO;
import com.mall.api.product.StockInDTO;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import com.mall.product.service.StockService;
import com.mall.product.service.ProductStatsService;
import com.mall.product.util.ProductJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务内部接口（实现 mall-api ProductFeignClient 契约，仅服务间调用，网关不暴露）
 * 扣减库存处于 Seata 全局事务内（order 发起，本服务为参与方）；回补/入库幂等（MQ 至少一次投递）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@RestController
@RequestMapping("/internal/product")
@RequiredArgsConstructor
public class ProductInternalController {

    private final StockService stockService;
    private final ProductSkuMapper skuMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductStatsService productStatsService;

    /** 单个 SKU 下单快照（校验上下架/价格/库存用） */
    @GetMapping("/sku-info")
    public Result<SkuOrderInfoDTO> getSkuOrderInfo(@RequestParam("skuId") Long skuId) {
        SkuOrderInfoDTO dto = buildSkuOrderInfo(skuId);
        if (dto == null) {
            throw new BizException("SKU 不存在");
        }
        return Result.success(dto);
    }

    /** 批量 SKU 下单快照（cart 列表 / portal 结算预览组装用；下架/停用也返回，由调用方按 status 标记失效） */
    @GetMapping("/sku-infos")
    public Result<List<SkuOrderInfoDTO>> getSkuOrderInfos(@RequestParam("skuIds") List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<SkuOrderInfoDTO> list = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getId, skuIds))
                .stream()
                .map(sku -> buildSkuOrderInfo(sku.getId()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    /** 扣减库存（change_type：1 下单扣减 / 4 秒杀扣减；行锁校验防超卖） */
    @PostMapping("/deduct-stock")
    public Result<Void> deductStock(@RequestBody DeductStockDTO dto) {
        int changeType = dto.getChangeType() == null ? 1 : dto.getChangeType();
        stockService.deductStock(dto.getBizSn(), dto.getSkuId(), dto.getQuantity(), changeType);
        return Result.success();
    }

    /** 是否存在秒杀扣减流水（seckill 关单回补判断：change_type=4 扣过才回补 sku.stock，防未扣先补虚增） */
    @GetMapping("/has-seckill-deducted")
    public Result<Boolean> hasSeckillDeducted(@RequestParam("bizSn") String bizSn, @RequestParam("skuId") Long skuId) {
        return Result.success(stockService.hasSeckillDeducted(bizSn, skuId));
    }

    /** 商品销量排行榜（10.5）：ZSET rank:sales 按销量倒序取 Top N */
    @GetMapping("/sales-rank")
    public Result<List<Map<String, Object>>> salesRank(@RequestParam(value = "topN", defaultValue = "10") int topN) {
        return Result.success(stockService.salesRank(Math.max(1, Math.min(topN, 50))));
    }

    /** 商品 PV / UV（10.2，看板商品统计） */
    @GetMapping("/stats/pv-uv")
    public Result<Map<String, Object>> pvUv(@RequestParam("spuId") Long spuId) {
        return Result.success(productStatsService.pvUv(spuId));
    }

    /** 商品浏览排行 Top N（10.2，看板商品统计） */
    @GetMapping("/stats/top-views")
    public Result<List<Map<String, Object>>> topViews(@RequestParam(value = "topN", defaultValue = "10") int topN) {
        return Result.success(productStatsService.topViews(Math.max(1, Math.min(topN, 50))));
    }

    /** 库存预警列表（看板：stock < low_stock，NULL 取全局阈值） */
    @GetMapping("/stats/warnings")
    public Result<List<Map<String, Object>>> stockWarnings() {
        return Result.success(stockService.warnings());
    }

    /** 回补库存（change_type：2取消回补 3退款回补 9秒杀回补；按 bizSn+changeType 幂等） */
    @PostMapping("/release-stock")
    public Result<Void> releaseStock(@RequestBody ReleaseStockDTO dto) {
        stockService.releaseStock(dto.getBizSn(), dto.getSkuId(), dto.getQuantity(), dto.getChangeType());
        return Result.success();
    }

    /** 入库（change_type=6 退货入库；按 bizSn 幂等） */
    @PostMapping("/stock-in")
    public Result<Void> stockIn(@RequestBody StockInDTO dto) {
        stockService.stockInReturn(dto.getBizSn(), dto.getSkuId(), dto.getQuantity());
        return Result.success();
    }

    /** 组装 SKU 下单快照（SPU 下架/SKU 停用不抛异常，status 字段标记，由调用方校验） */
    private SkuOrderInfoDTO buildSkuOrderInfo(Long skuId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            return null;
        }
        ProductSpu spu = spuMapper.selectById(sku.getSpuId());
        SkuOrderInfoDTO dto = new SkuOrderInfoDTO();
        dto.setSkuId(sku.getId());
        dto.setSkuCode(sku.getSkuCode());
        dto.setSpuId(spu == null ? null : spu.getId());
        dto.setSpuName(spu == null ? null : spu.getName());
        dto.setSpec(ProductJsonUtil.unwrapText(sku.getSpec()));
        dto.setPic(sku.getPic());
        dto.setPrice(sku.getPrice());
        dto.setStock(sku.getStock());
        dto.setStatus(sku.getStatus());
        dto.setSpuStatus(spu == null ? 0 : spu.getStatus());
        return dto;
    }
}
