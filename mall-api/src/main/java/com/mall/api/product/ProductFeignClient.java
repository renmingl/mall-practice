package com.mall.api.product;

import com.mall.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 商品服务内部契约（order 扣/回库存、cart 组装购物车、portal 结算预览调用）
 * 说明：库存只经本契约变动（扣减/回补/退货入库），流水留痕在 product 侧（stock_log 对账）；
 * 扣库存方法处于 Seata 全局事务内（order 发起，product 为参与方）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@FeignClient(name = "mall-product", path = "/internal/product", contextId = "productFeignClient")
public interface ProductFeignClient {

    /** 单个 SKU 下单快照（校验上下架/价格/库存用） */
    @GetMapping("/sku-info")
    Result<SkuOrderInfoDTO> getSkuOrderInfo(@RequestParam("skuId") Long skuId);

    /** 批量 SKU 下单快照（cart 列表 / portal 结算预览组装用） */
    @GetMapping("/sku-infos")
    Result<List<SkuOrderInfoDTO>> getSkuOrderInfos(@RequestParam("skuIds") List<Long> skuIds);

    /** 扣减库存（change_type：1 下单扣减 / 4 秒杀扣减；条件原子更新 stock>=quantity，防超卖） */
    @PostMapping("/deduct-stock")
    Result<Void> deductStock(@RequestBody DeductStockDTO dto);

    /** 是否存在秒杀扣减流水（seckill 关单回补判断：change_type=4 扣过才回补 sku.stock，防未扣先补虚增） */
    @GetMapping("/has-seckill-deducted")
    Result<Boolean> hasSeckillDeducted(@RequestParam("bizSn") String bizSn, @RequestParam("skuId") Long skuId);

    /** 商品销量排行榜（10.5：ZSET rank:sales 按销量倒序取 Top N） */
    @GetMapping("/sales-rank")
    Result<List<Map<String, Object>>> salesRank(@RequestParam(value = "topN", defaultValue = "10") int topN);

    /** 商品 PV / UV（10.2：看板商品统计） */
    @GetMapping("/stats/pv-uv")
    Result<Map<String, Object>> pvUv(@RequestParam("spuId") Long spuId);

    /** 商品浏览排行 Top N（10.2：看板商品统计） */
    @GetMapping("/stats/top-views")
    Result<List<Map<String, Object>>> topViews(@RequestParam(value = "topN", defaultValue = "10") int topN);

    /** 库存预警列表（看板：stock < low_stock） */
    @GetMapping("/stats/warnings")
    Result<List<Map<String, Object>>> stockWarnings();

    /** 回补库存（change_type：2取消回补 3退款回补 9秒杀回补） */
    @PostMapping("/release-stock")
    Result<Void> releaseStock(@RequestBody ReleaseStockDTO dto);

    /** 入库（change_type=6 退货入库，退款退货确认收货联动） */
    @PostMapping("/stock-in")
    Result<Void> stockIn(@RequestBody StockInDTO dto);
}
