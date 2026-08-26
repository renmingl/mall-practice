package com.mall.mbg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mbg.entity.ProductSku;

/**
 * 商品SKU表（库存随下单扣减，秒杀场景由 Redis 预扣） Mapper 接口
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

}
