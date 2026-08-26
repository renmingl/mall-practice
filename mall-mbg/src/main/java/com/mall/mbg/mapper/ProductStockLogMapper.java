package com.mall.mbg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mbg.entity.ProductStockLog;

/**
 * 库存流水表（扣减/回补/采购入库/退货入库/盘点对账，防超卖审计） Mapper 接口
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public interface ProductStockLogMapper extends BaseMapper<ProductStockLog> {

}
