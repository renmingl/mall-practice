package com.mall.mbg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mbg.entity.OrderStatusLog;

/**
 * 订单状态流转日志表（状态机审计，防乱改状态） Mapper 接口
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

}
