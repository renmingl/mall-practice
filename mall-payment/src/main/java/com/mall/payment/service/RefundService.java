package com.mall.payment.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.order.OrderFeignClient;
import com.mall.api.order.OrderInfoDTO;
import com.mall.api.order.OrderItemInfoDTO;
import com.mall.common.exception.BizException;
import com.mall.common.mq.MqTopics;
import com.mall.common.mq.TxMessageService;
import com.mall.mbg.entity.Payment;
import com.mall.mbg.entity.PaymentRefund;
import com.mall.mbg.mapper.PaymentMapper;
import com.mall.mbg.mapper.PaymentRefundMapper;
import com.mall.payment.dto.RefundApplyDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款服务（阶段 6）：整单退款状态机 + 退款联动回补
 * 状态机：0申请中 → 1审核通过 → 2退货中（仅退货退款）→ 3退款中 → 4已退款；5已拒绝
 * 仅退款：审核通过后直接执行退款（0→1→3→4）；退货退款：审核通过（1）→ 后台确认退货（1→3）→ 执行退款（3→4）
 * 退款成功联动（本地消息表四条消息，事务提交后发送；Feign 回写订单双保险）：
 * STOCK_REFUND（回补库存，带明细） / COUPON_REFUND（退券） / MEMBER_POINTS（扣回积分） / ORDER_REFUND（回写订单）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final OrderFeignClient orderFeignClient;
    private final TxMessageService txMessageService;

    /** 申请退款（整单退款）：校验订单已支付且未退款，同订单仅允许一条进行中的退款单 */
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long memberId, RefundApplyDTO dto) {
        if (dto.getRefundType() != 1 && dto.getRefundType() != 2) {
            throw new BizException("退款类型仅支持 1仅退款 / 2退货退款");
        }
        if (dto.getRefundType() == 2
                && (!StringUtils.hasText(dto.getReturnCompany()) || !StringUtils.hasText(dto.getReturnSn()))) {
            throw new BizException("退货退款须填写退货物流公司与单号");
        }
        OrderInfoDTO order = orderFeignClient.getOrderInfo(dto.getOrderSn()).getDataOrThrow();
        if (!order.getMemberId().equals(memberId)) {
            throw new BizException("无权操作该订单");
        }
        if (order.getStatus() != 1 && order.getStatus() != 2 && order.getStatus() != 3) {
            throw new BizException("订单当前状态不可申请退款");
        }
        Long pending = refundMapper.selectCount(Wrappers.<PaymentRefund>lambdaQuery()
                .eq(PaymentRefund::getOrderSn, dto.getOrderSn())
                .in(PaymentRefund::getStatus, (byte) 0, (byte) 1, (byte) 2, (byte) 3));
        if (pending != null && pending > 0) {
            throw new BizException("该订单已有进行中的退款申请");
        }
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderSn, dto.getOrderSn())
                .eq(Payment::getStatus, 1)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException("未找到已支付的支付流水");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundSn(PaymentService.generateSn("R"));
        refund.setOrderId(order.getOrderId());
        refund.setOrderSn(dto.getOrderSn());
        refund.setPaymentSn(payment.getPaymentSn());
        refund.setMemberId(memberId);
        refund.setRefundAmount(order.getPayAmount());
        refund.setReason(dto.getReason());
        refund.setRefundType(dto.getRefundType());
        refund.setReturnCompany(dto.getReturnCompany());
        refund.setReturnSn(dto.getReturnSn());
        refund.setStatus((byte) 0);
        refund.setApplyTime(LocalDateTime.now());
        refundMapper.insert(refund);
        log.info("申请退款 refundSn={} orderSn={} type={}", refund.getRefundSn(), dto.getOrderSn(), dto.getRefundType());
    }

    /** 后台审核：0→1 通过 / 0→5 拒绝；仅退款审核通过后直接执行退款 */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean approved, String auditBy) {
        PaymentRefund refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BizException("退款单不存在");
        }
        if (refund.getStatus() != 0) {
            throw new BizException("退款单当前状态不可审核");
        }
        if (!approved) {
            int rows = refundMapper.update(null, new UpdateWrapper<PaymentRefund>()
                    .eq("id", id).eq("status", 0)
                    .set("status", 5)
                    .set("audit_by", auditBy)
                    .set("audit_time", LocalDateTime.now()));
            if (rows == 0) {
                throw new BizException("退款单状态已变化");
            }
            log.info("退款审核拒绝 refundSn={}", refund.getRefundSn());
            return;
        }
        int rows = refundMapper.update(null, new UpdateWrapper<PaymentRefund>()
                .eq("id", id).eq("status", 0)
                .set("status", 1)
                .set("audit_by", auditBy)
                .set("audit_time", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("退款单状态已变化");
        }
        log.info("退款审核通过 refundSn={}", refund.getRefundSn());
        // 仅退款：审核通过即执行退款（跳过退货环节）；退货退款等待后台确认退货
        if (refund.getRefundType() == 1) {
            executeRefund(refund.getRefundSn());
        }
    }

    /** 重试执行退款（补偿入口）：仅退款审核通过后执行失败/超时的场景（退款单停留 1 或 3 状态），
     *  重新走 executeRefund（内部条件更新幂等，已退款直接返回） */
    @Transactional(rollbackFor = Exception.class)
    public void retryExecute(Long id) {
        PaymentRefund refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BizException("退款单不存在");
        }
        if (refund.getStatus() != 1 && refund.getStatus() != 3) {
            throw new BizException("退款单当前状态不可执行退款");
        }
        executeRefund(refund.getRefundSn());
    }

    /** 后台确认退货（退货退款）：1→3 退款中 → 执行退款 3→4 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long id) {
        PaymentRefund refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BizException("退款单不存在");
        }
        if (refund.getRefundType() != 2) {
            throw new BizException("仅退货退款需要确认退货");
        }
        if (!StringUtils.hasText(refund.getReturnCompany()) || !StringUtils.hasText(refund.getReturnSn())) {
            throw new BizException("退货物流信息缺失");
        }
        if (refund.getStatus() != 1) {
            throw new BizException("退款单当前状态不可确认退货");
        }
        int rows = refundMapper.update(null, new UpdateWrapper<PaymentRefund>()
                .eq("id", id).eq("status", 1)
                .set("status", 3));
        if (rows == 0) {
            throw new BizException("退款单状态已变化");
        }
        log.info("确认退货完成 refundSn={}，执行退款", refund.getRefundSn());
        executeRefund(refund.getRefundSn());
    }

    /**
     * 执行退款（内部）：3→4 + 支付流水 1→3 + Feign 回写订单 + 本地消息表四条联动消息。
     * 状态机条件更新保证只执行一次（幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeRefund(String refundSn) {
        PaymentRefund refund = refundMapper.selectOne(Wrappers.<PaymentRefund>lambdaQuery()
                .eq(PaymentRefund::getRefundSn, refundSn));
        if (refund == null) {
            throw new BizException("退款单不存在");
        }
        // 幂等：已退款直接返回
        if (refund.getStatus() == 4) {
            return;
        }
        int rows = refundMapper.update(null, new UpdateWrapper<PaymentRefund>()
                .eq("id", refund.getId())
                .in("status", 1, 3)   // 仅退款：1 直接进入；退货退款：3
                .set("status", 3));
        if (rows == 0) {
            throw new BizException("退款单状态已变化，无法执行退款");
        }
        // 支付流水 1→3 已退款
        paymentMapper.update(null, new UpdateWrapper<Payment>()
                .eq("payment_sn", refund.getPaymentSn())
                .eq("status", 1)
                .set("status", 3));
        // 退款单 3→4
        rows = refundMapper.update(null, new UpdateWrapper<PaymentRefund>()
                .eq("id", refund.getId()).eq("status", 3)
                .set("status", 4)
                .set("refund_time", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("退款单状态已变化");
        }
        // 取订单明细（随消息投递给 product 回补库存）
        List<OrderItemInfoDTO> items = orderFeignClient.getOrderItems(refund.getOrderSn()).getDataOrThrow();
        // Feign 回写订单 1/2/3→5（同步；MQ 消息双保险）
        orderFeignClient.markRefunded(refund.getOrderSn());
        // 本地消息表四条联动（事务提交后发送，消费端幂等）
        String bizPrefix = "REFUND_" + refund.getRefundSn();
        txMessageService.saveAndSendOnCommit(bizPrefix + "_STOCK",
                MqTopics.STOCK_REFUND, MqTopics.TAG_REFUND,
                Map.of("orderSn", refund.getOrderSn(), "items", items));
        txMessageService.saveAndSendOnCommit(bizPrefix + "_COUPON",
                MqTopics.COUPON_REFUND, MqTopics.TAG_REFUND,
                Map.of("orderId", refund.getOrderId(), "memberId", refund.getMemberId()));
        txMessageService.saveAndSendOnCommit(bizPrefix + "_POINTS",
                MqTopics.MEMBER_POINTS, MqTopics.TAG_REFUND,
                Map.of("memberId", refund.getMemberId(), "orderSn", refund.getOrderSn(),
                        "payAmount", refund.getRefundAmount()));
        txMessageService.saveAndSendOnCommit(bizPrefix + "_ORDER",
                MqTopics.ORDER_REFUND, MqTopics.TAG_REFUND,
                Map.of("orderSn", refund.getOrderSn()));
        log.info("退款执行成功 refundSn={} orderSn={} amount={}", refund.getRefundSn(), refund.getOrderSn(), refund.getRefundAmount());
    }

    /** 我的退款单分页 */
    public Page<Map<String, Object>> pageMine(Long memberId, long page, long size) {
        Page<PaymentRefund> refundPage = refundMapper.selectPage(new Page<>(page, size),
                Wrappers.<PaymentRefund>lambdaQuery()
                        .eq(PaymentRefund::getMemberId, memberId)
                        .orderByDesc(PaymentRefund::getId));
        return toPage(refundPage);
    }

    /** 后台退款单分页（按订单号/状态筛选） */
    public Page<Map<String, Object>> adminPage(String orderSn, Integer status, long page, long size) {
        Page<PaymentRefund> refundPage = refundMapper.selectPage(new Page<>(page, size),
                Wrappers.<PaymentRefund>lambdaQuery()
                        .like(StringUtils.hasText(orderSn), PaymentRefund::getOrderSn, orderSn)
                        .eq(status != null, PaymentRefund::getStatus, status)
                        .orderByDesc(PaymentRefund::getId));
        return toPage(refundPage);
    }

    private Page<Map<String, Object>> toPage(Page<PaymentRefund> source) {
        List<Map<String, Object>> data = source.getRecords().stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("refundSn", r.getRefundSn());
            row.put("orderId", r.getOrderId());
            row.put("orderSn", r.getOrderSn());
            row.put("paymentSn", r.getPaymentSn());
            row.put("memberId", r.getMemberId());
            row.put("refundAmount", r.getRefundAmount());
            row.put("reason", r.getReason());
            row.put("refundType", r.getRefundType());
            row.put("returnCompany", r.getReturnCompany());
            row.put("returnSn", r.getReturnSn());
            row.put("status", r.getStatus());
            row.put("auditBy", r.getAuditBy());
            row.put("auditTime", r.getAuditTime());
            row.put("applyTime", r.getApplyTime());
            row.put("refundTime", r.getRefundTime());
            row.put("createTime", r.getCreateTime());
            return row;
        }).toList();
        Page<Map<String, Object>> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(data);
        return result;
    }
}
