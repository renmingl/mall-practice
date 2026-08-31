package com.mall.payment.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.api.order.OrderFeignClient;
import com.mall.api.order.OrderInfoDTO;
import com.mall.api.payment.CreatePaymentDTO;
import com.mall.api.payment.PaymentDTO;
import com.mall.common.exception.BizException;
import com.mall.common.mq.MqTopics;
import com.mall.common.mq.TxMessageService;
import com.mall.mbg.entity.Payment;
import com.mall.mbg.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务：创建支付流水（幂等）/模拟第三方回调（trade_no 幂等）/查单（轮询 + 回写补偿）
 * 回调成功链路：payment 0→1（条件更新）→ Feign 回写订单 markPaid → 本地消息表发返积分消息（事务提交后发送）
 * 查单兜底：支付单已成功但订单未回写（Feign 失败场景）→ 查询接口/定时任务补偿回写
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderFeignClient orderFeignClient;
    private final TxMessageService txMessageService;

    /** 创建支付单（order 拉起收银台调用；幂等：同订单+同支付方式复用已存在流水） */
    @Transactional(rollbackFor = Exception.class)
    public PaymentDTO createPayment(CreatePaymentDTO dto) {
        Payment exist = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, dto.getOrderId())
                .eq(Payment::getPayType, dto.getPayType())
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (exist != null) {
            return toDTO(exist);
        }
        Payment payment = new Payment();
        payment.setPaymentSn(generateSn("P"));
        payment.setOrderId(dto.getOrderId());
        payment.setOrderSn(dto.getOrderSn());
        payment.setMemberId(dto.getMemberId());
        payment.setPayAmount(dto.getPayAmount());
        payment.setPayType(dto.getPayType());
        payment.setStatus((byte) 0);
        paymentMapper.insert(payment);
        log.info("创建支付单 paymentSn={} orderSn={} payAmount={}", payment.getPaymentSn(), dto.getOrderSn(), dto.getPayAmount());
        return toDTO(payment);
    }

    /**
     * 模拟第三方支付回调（演示入口；生产由支付宝/微信异步通知替换，须验签 + 金额核对）
     * 幂等：payment_sn + status=0 条件更新 0→1，重复回调直接返回
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentDTO mockCallback(String paymentSn, String tradeNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPaymentSn, paymentSn));
        if (payment == null) {
            throw new BizException("支付流水不存在");
        }
        if (payment.getStatus() == 1) {
            return toDTO(payment); // 幂等：已支付成功
        }
        if (payment.getStatus() != 0) {
            throw new BizException("支付流水状态异常，不可回调");
        }
        // 校验订单仍待付款（防重复支付/已取消订单回调）
        OrderInfoDTO order = orderFeignClient.getOrderInfo(payment.getOrderSn()).getDataOrThrow();
        if (order.getStatus() != 0) {
            throw new BizException("订单状态已变化，支付回调失败");
        }
        String finalTradeNo = (tradeNo == null || tradeNo.isBlank())
                ? "MOCK" + System.currentTimeMillis() : tradeNo;
        // 条件更新 0→1（幂等，防并发回调重复处理）
        int rows = paymentMapper.update(null, new UpdateWrapper<Payment>()
                .eq("id", payment.getId())
                .eq("status", 0)
                .set("status", 1)
                .set("trade_no", finalTradeNo)
                .set("notify_time", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("支付流水状态已变化，请刷新后重试");
        }
        // 回写订单 0→1（Feign 同步；失败由查单兜底/定时补偿重试）
        orderFeignClient.markPaid(payment.getOrderSn(), payment.getPayType(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        // 本地消息表：支付成功返积分（member 消费，事务提交后发送）
        txMessageService.saveAndSendOnCommit("POINTS_PAID_" + payment.getOrderSn(),
                MqTopics.MEMBER_POINTS, MqTopics.TAG_PAID,
                Map.of("memberId", payment.getMemberId(),
                        "orderSn", payment.getOrderSn(),
                        "payAmount", payment.getPayAmount()));
        log.info("模拟支付回调成功 paymentSn={} tradeNo={} orderSn={}", paymentSn, finalTradeNo, payment.getOrderSn());
        return toDTO(paymentMapper.selectById(payment.getId()));
    }

    /** 查单（支付结果页轮询；带回写补偿：支付单成功但订单未回写时补 markPaid） */
    public PaymentDTO queryByOrder(Long memberId, String orderSn) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderSn, orderSn)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException("支付流水不存在");
        }
        if (!payment.getMemberId().equals(memberId)) {
            throw new BizException("无权查看该支付流水");
        }
        compensateOrderWriteBack(payment);
        return toDTO(payment);
    }

    /** 内部查询（portal 支付结果页/网关侧查询；不校验会员归属） */
    public PaymentDTO getByOrder(String orderSn) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderSn, orderSn)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException("支付流水不存在");
        }
        compensateOrderWriteBack(payment);
        return toDTO(payment);
    }

    /** 定时补偿：支付单已成功但订单仍待付款 → 补回写（回调后 Feign 失败/宕机场景） */
    public int compensateWriteBack() {
        java.util.List<Payment> paid = paymentMapper.selectList(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getStatus, 1)
                .last("LIMIT 100"));
        int count = 0;
        for (Payment payment : paid) {
            try {
                if (compensateOrderWriteBack(payment)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("支付回写补偿失败 paymentSn={}", payment.getPaymentSn(), e);
            }
        }
        if (count > 0) {
            log.info("支付回写补偿完成 count={}", count);
        }
        return count;
    }

    /** 单笔补偿：订单仍待付款则补 markPaid；返回是否补偿 */
    private boolean compensateOrderWriteBack(Payment payment) {
        if (payment.getStatus() != 1) {
            return false;
        }
        OrderInfoDTO order = orderFeignClient.getOrderInfo(payment.getOrderSn()).getDataOrThrow();
        if (order.getStatus() != 0) {
            return false;
        }
        orderFeignClient.markPaid(payment.getOrderSn(), payment.getPayType(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("查单补偿：订单状态补回写 paymentSn={} orderSn={}", payment.getPaymentSn(), payment.getOrderSn());
        return true;
    }

    /** 支付流水转契约 DTO */
    public static PaymentDTO toDTO(Payment payment) {
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setPaymentSn(payment.getPaymentSn());
        dto.setOrderId(payment.getOrderId());
        dto.setOrderSn(payment.getOrderSn());
        dto.setMemberId(payment.getMemberId());
        dto.setPayAmount(payment.getPayAmount());
        dto.setPayType(payment.getPayType());
        dto.setTradeNo(payment.getTradeNo());
        dto.setStatus(payment.getStatus());
        dto.setNotifyTime(payment.getNotifyTime());
        dto.setCreateTime(payment.getCreateTime());
        return dto;
    }

    /** 业务单号生成：前缀 + yyyyMMddHHmmss + 4 位随机（uk 唯一键兜底防撞） */
    public static String generateSn(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
