package com.mall.member.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mall.mbg.entity.Member;
import com.mall.mbg.entity.MemberPointLog;
import com.mall.mbg.mapper.MemberMapper;
import com.mall.mbg.mapper.MemberPointLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 积分变动服务（阶段 6）：支付成功返积分 / 退款成功扣回
 * 规则：积分 = 实付金额（取整元） × 会员等级积分倍率（普通1.0/白银1.2/黄金1.5/钻石2.0），与个人中心权益表一致
 * 幂等：member_point_log 以 order_sn + change_type 判重（MQ 至少一次投递，消费端去重）
 * 余额更新用 SQL 原子增减（points = points + N / GREATEST(points - N, 0)），防并发丢更新
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPointsService {

    /** 变动类型：2购物返积分 4退款扣回（1注册赠送 3兑换消耗 由注册/兑换流程写入） */
    private static final byte CHANGE_EARN = 2;
    private static final byte CHANGE_DEDUCT = 4;

    /** 会员等级积分倍率（与 MemberProfileService.LEVEL_INFO 一致） */
    private static final BigDecimal[] LEVEL_RATE = {
            BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.2),
            BigDecimal.valueOf(1.5), BigDecimal.valueOf(2.0)};

    private final MemberMapper memberMapper;
    private final MemberPointLogMapper pointLogMapper;

    /** 支付成功返积分（TAG=PAID，payment 回调后经本地消息表投递） */
    @Transactional(rollbackFor = Exception.class)
    public void earn(Long memberId, String orderSn, BigDecimal payAmount) {
        if (hasLog(memberId, orderSn, CHANGE_EARN)) {
            log.info("返积分消息重复，跳过 orderSn={} memberId={}", orderSn, memberId);
            return;
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            log.warn("返积分目标会员不存在，忽略 memberId={} orderSn={}", memberId, orderSn);
            return;
        }
        int points = calcPoints(member.getLevel(), payAmount);
        if (points <= 0) {
            return;
        }
        memberMapper.update(null, new UpdateWrapper<Member>()
                .eq("id", memberId)
                .setSql("points = points + " + points));
        insertLog(memberId, orderSn, CHANGE_EARN, points);
        log.info("支付返积分成功 memberId={} orderSn={} points={}", memberId, orderSn, points);
    }

    /** 退款成功扣回积分（TAG=REFUND，payment 退款成功后经本地消息表投递；扣至 0 不为负） */
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long memberId, String orderSn, BigDecimal payAmount) {
        if (hasLog(memberId, orderSn, CHANGE_DEDUCT)) {
            log.info("扣回积分消息重复，跳过 orderSn={} memberId={}", orderSn, memberId);
            return;
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            log.warn("扣回积分目标会员不存在，忽略 memberId={} orderSn={}", memberId, orderSn);
            return;
        }
        int points = calcPoints(member.getLevel(), payAmount);
        if (points <= 0) {
            return;
        }
        memberMapper.update(null, new UpdateWrapper<Member>()
                .eq("id", memberId)
                .setSql("points = GREATEST(points - " + points + ", 0)"));
        insertLog(memberId, orderSn, CHANGE_DEDUCT, -points);
        log.info("退款扣回积分成功 memberId={} orderSn={} points=-{}", memberId, orderSn, points);
    }

    /** 积分计算：实付金额取整元 × 等级倍率（向下取整） */
    private int calcPoints(Byte level, BigDecimal payAmount) {
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int idx = level == null ? 0 : Math.min(Math.max(level, 0), LEVEL_RATE.length - 1);
        return payAmount.setScale(0, RoundingMode.DOWN)
                .multiply(LEVEL_RATE[idx])
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }

    /** 幂等判重：同会员 + 同订单 + 同变动类型已入流水则视为已处理 */
    private boolean hasLog(Long memberId, String orderSn, byte changeType) {
        Long count = pointLogMapper.selectCount(new LambdaQueryWrapper<MemberPointLog>()
                .eq(MemberPointLog::getMemberId, memberId)
                .eq(MemberPointLog::getOrderSn, orderSn)
                .eq(MemberPointLog::getChangeType, changeType));
        return count != null && count > 0;
    }

    /** 写积分流水（余额取更新后的最新值） */
    private void insertLog(Long memberId, String orderSn, byte changeType, int changePoint) {
        Member after = memberMapper.selectById(memberId);
        MemberPointLog logEntity = new MemberPointLog();
        logEntity.setMemberId(memberId);
        logEntity.setChangeType(changeType);
        logEntity.setChangePoint(changePoint);
        logEntity.setPointAfter(after == null ? 0 : after.getPoints());
        logEntity.setOrderSn(orderSn);
        pointLogMapper.insert(logEntity);
    }
}
