package com.mall.mbg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志表（状态机审计，防乱改状态）
 *
 * @author renmingl
 * @date 2026-08-26 00:27:53
 */
@Getter
@Setter
@ToString
@TableName("order_status_log")
public class OrderStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 变更前状态
     */
    private Byte fromStatus;

    /**
     * 变更后状态
     */
    private Byte toStatus;

    /**
     * 操作者：买家/系统/管理员
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 变更时间
     */
    private LocalDateTime createTime;
}
