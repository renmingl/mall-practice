package com.mall.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 下单请求（结算页提交）：requestId 为幂等键（客户端生成，防重复下单），
 * 商品取自购物车勾选条目（服务端拉取，不信任前端金额）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Data
public class OrderCreateDTO {

    /** 幂等键（客户端生成，防重复提交，下单必传） */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 优惠券领取记录 ID（可选；下单时锁券，支付成功核销） */
    private Long couponUserId;

    /** 收货人（下单快照） */
    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    /** 收货电话 */
    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    /** 收货地址（省市区+详细，前端拼接） */
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /** 买家备注（可选） */
    private String remark;
}
