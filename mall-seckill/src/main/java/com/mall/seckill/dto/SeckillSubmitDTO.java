package com.mall.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 秒杀提交 DTO（前台，14.4）：收货信息随提交带入 MQ 消息体，order 异步落单时快照
 * @author renmingl
 * @date 2026-09-01 10:00:00
 */
@Getter
@Setter
public class SeckillSubmitDTO {

    /** 秒杀商品 ID */
    @NotNull(message = "秒杀商品必选")
    private Long seckillProductId;

    /** 购买数量（默认 1，须 ≤ limit_per_user） */
    @Min(value = 1, message = "购买数量至少 1")
    private Integer quantity = 1;

    /** 幂等 token（秒杀页进入时获取，12.3 防重复提交） */
    private String token;

    /** 收货人（下单快照） */
    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    /** 收货电话 */
    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    /** 收货地址（省市区+详细，前端拼接） */
    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;
}
