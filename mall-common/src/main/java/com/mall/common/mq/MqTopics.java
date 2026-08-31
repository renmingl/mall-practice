package com.mall.common.mq;

/**
 * MQ Topic/Tag 常量（全链路消息路由约定，消费端 binding destination 与此一一对应）
 * 延迟关单：order 下单成功后发延迟消息（rocketmq_DELAY=16，即 30 分钟）→ order 消费检查未支付则关单
 * 退款联动：payment 退款成功写 tx_message 四条消息 → product/coupon/member/order 各自消费补偿
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
public final class MqTopics {

    private MqTopics() {
    }

    /** 延迟关单 Topic（order 发/消费；tag=CLOSE，body={orderSn}） */
    public static final String ORDER_CLOSE = "mall-order-close-topic";

    /** 退款成功回写订单 Topic（payment 发/order 消费；tag=REFUND，body={orderSn}） */
    public static final String ORDER_REFUND = "mall-order-refund-topic";

    /** 退款回补库存 Topic（payment 发/product 消费；tag=REFUND，body={orderSn, items:[{orderItemId, skuId, quantity}]}） */
    public static final String STOCK_REFUND = "mall-stock-refund-topic";

    /** 退款退券 Topic（payment 发/coupon 消费；tag=REFUND，body={orderId, memberId}） */
    public static final String COUPON_REFUND = "mall-coupon-refund-topic";

    /** 积分变动 Topic（payment 发/member 消费；tag=PAID 返积分 / REFUND 扣回，body={memberId, orderSn, payAmount}） */
    public static final String MEMBER_POINTS = "mall-member-points-topic";

    /** Tag：支付成功（返积分） */
    public static final String TAG_PAID = "PAID";

    /** Tag：退款（扣回/回补） */
    public static final String TAG_REFUND = "REFUND";

    /** Tag：关单 */
    public static final String TAG_CLOSE = "CLOSE";

    /** RocketMQ 默认延迟级别 16 = 30 分钟（超时关单窗口） */
    public static final int DELAY_LEVEL_30M = 16;

    /** 本地消息表重发上限（超过后人工介入） */
    public static final int TX_MESSAGE_MAX_RETRY = 5;
}
