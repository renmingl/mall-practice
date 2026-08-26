package com.mall.mbg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mbg.entity.PaymentRefund;

/**
 * 退款单表（整单退款状态机；退款成功后回补库存、退回优惠券——退券校验有效期，过期置已过期） Mapper 接口
 *
 * @author renmingl
 * @since 2026-08-26 00:27:53
 */
public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {

}
