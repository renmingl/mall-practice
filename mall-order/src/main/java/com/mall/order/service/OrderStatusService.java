package com.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mall.mbg.entity.OrderStatusLog;
import com.mall.mbg.entity.Orders;
import com.mall.mbg.mapper.OrderStatusLogMapper;
import com.mall.mbg.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 订单状态机：条件更新（幂等 + 兼容 Seata AT 回滚）+ 流水审计
 * 流转：0待付款 → 1待发货 → 2待收货 → 3已完成；0→4已取消；1/2/3→5已退款
 * 所有流转均为「主键等值 + 前置状态条件」更新，重复调用不产生副作用；
 * 每次流转成功写 order_status_log（状态机审计，防乱改状态）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrdersMapper ordersMapper;
    private final OrderStatusLogMapper statusLogMapper;

    /** 创建订单流水（from=null, to=0） */
    public void logCreate(Orders order) {
        insertLog(order.getId(), order.getOrderSn(), null, (byte) 0, "买家", "创建订单");
    }

    /** 支付成功：0→1，记录支付方式/时间 */
    public boolean markPaid(Orders order, Byte payType, LocalDateTime payTime) {
        int rows = ordersMapper.update(null, new UpdateWrapper<Orders>()
                .eq("id", order.getId())
                .eq("status", 0)
                .set("status", 1)
                .set("pay_type", payType)
                .set("pay_time", payTime));
        if (rows > 0) {
            insertLog(order.getId(), order.getOrderSn(), (byte) 0, (byte) 1, "系统", "支付成功");
        }
        return rows > 0;
    }

    /** 整单退款：1/2/3→5（以订单当前状态为前置条件） */
    public boolean markRefunded(Orders order) {
        Byte from = order.getStatus();
        int rows = ordersMapper.update(null, new UpdateWrapper<Orders>()
                .eq("id", order.getId())
                .eq("status", from)
                .set("status", 5));
        if (rows > 0) {
            insertLog(order.getId(), order.getOrderSn(), from, (byte) 5, "系统", "整单退款成功");
        }
        return rows > 0;
    }

    /** 发货：1→2，记录物流信息 */
    public boolean deliver(Orders order, String company, String sn) {
        int rows = ordersMapper.update(null, new UpdateWrapper<Orders>()
                .eq("id", order.getId())
                .eq("status", 1)
                .set("status", 2)
                .set("delivery_company", company)
                .set("delivery_sn", sn)
                .set("delivery_time", LocalDateTime.now()));
        if (rows > 0) {
            insertLog(order.getId(), order.getOrderSn(), (byte) 1, (byte) 2, "管理员", "发货");
        }
        return rows > 0;
    }

    /** 确认收货（买家/超时自动）：2→3 */
    public boolean receive(Orders order) {
        int rows = ordersMapper.update(null, new UpdateWrapper<Orders>()
                .eq("id", order.getId())
                .eq("status", 2)
                .set("status", 3)
                .set("receive_time", LocalDateTime.now()));
        if (rows > 0) {
            insertLog(order.getId(), order.getOrderSn(), (byte) 2, (byte) 3, "买家", "确认收货");
        }
        return rows > 0;
    }

    /** 取消（买家/超时关单）：0→4 */
    public boolean cancel(Orders order, String operator, String remark) {
        int rows = ordersMapper.update(null, new UpdateWrapper<Orders>()
                .eq("id", order.getId())
                .eq("status", 0)
                .set("status", 4));
        if (rows > 0) {
            insertLog(order.getId(), order.getOrderSn(), (byte) 0, (byte) 4, operator, remark);
        }
        return rows > 0;
    }

    private void insertLog(Long orderId, String orderSn, Byte from, Byte to, String operator, String remark) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setOrderSn(orderSn);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator(operator);
        log.setRemark(remark);
        statusLogMapper.insert(log);
    }
}
