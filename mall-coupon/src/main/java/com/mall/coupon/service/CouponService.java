package com.mall.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.coupon.CouponAvailableDTO;
import com.mall.common.exception.BizException;
import com.mall.coupon.dto.CouponSaveDTO;
import com.mall.mbg.entity.Coupon;
import com.mall.mbg.entity.CouponUser;
import com.mall.mbg.entity.Orders;
import com.mall.mbg.mapper.CouponMapper;
import com.mall.mbg.mapper.CouponUserMapper;
import com.mall.mbg.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券服务：模板管理、领券（SETNX 幂等 + 条件更新防超领）、锁券/核销/退回、过期扫描、优惠计算
 * 状态机：0未使用 → 1已锁定（下单占用）→ 2已使用；取消/超时关单 1→0；退款退回 2→0（过期置3）
 * 防超领：UPDATE coupon SET received_count=received_count+1 WHERE received_count &lt; total_count（原子条件更新）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    /** 领券幂等键（防同一用户并发重复领取；领取事务提交后删除，per_limit>1 时可再次领取） */
    private static final String RECEIVE_ONCE_KEY = "coupon:receive:once:";
    private static final long RECEIVE_ONCE_TTL_SECONDS = 30;

    private final CouponMapper couponMapper;
    private final CouponUserMapper couponUserMapper;
    private final OrdersMapper ordersMapper;
    private final StringRedisTemplate redisTemplate;

    // ==================== 后台模板管理 ====================

    /** 模板分页（支持名称/状态筛选） */
    public Page<Coupon> adminPage(String name, Integer status, long page, long size) {
        return couponMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Coupon>()
                        .like(StringUtils.hasText(name), Coupon::getName, name)
                        .eq(status != null, Coupon::getStatus, status)
                        .orderByDesc(Coupon::getCreateTime));
    }

    /** 新增/修改模板（修改仅允许调整基础配置，已领取数量/状态不动） */
    public void save(CouponSaveDTO dto) {
        if (dto.getType() == null || (dto.getType() != 1 && dto.getType() != 2)) {
            throw new BizException("券类型仅支持 1满减券 / 2折扣券");
        }
        if (dto.getThreshold() == null) {
            dto.setThreshold(BigDecimal.ZERO);
        }
        if (dto.getThreshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("使用门槛不能为负数");
        }
        if (dto.getUseEndTime().isBefore(dto.getUseStartTime())) {
            throw new BizException("失效时间必须晚于生效时间");
        }
        Coupon coupon = new Coupon();
        coupon.setId(dto.getId());
        coupon.setName(dto.getName());
        coupon.setType(dto.getType());
        coupon.setAmount(dto.getAmount());
        coupon.setThreshold(dto.getThreshold());
        coupon.setTotalCount(dto.getTotalCount());
        coupon.setPerLimit(dto.getPerLimit());
        coupon.setUseStartTime(dto.getUseStartTime());
        coupon.setUseEndTime(dto.getUseEndTime());
        if (dto.getId() == null) {
            coupon.setReceivedCount(0);
            coupon.setStatus((byte) 1);
            couponMapper.insert(coupon);
        } else {
            Coupon exist = couponMapper.selectById(dto.getId());
            if (exist == null) {
                throw new BizException("券模板不存在");
            }
            if (exist.getReceivedCount() > dto.getTotalCount()) {
                throw new BizException("发行总量不能小于已领取数量");
            }
            couponMapper.updateById(coupon);
        }
    }

    /** 模板状态：1进行中 0已结束 */
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态仅支持 1进行中 / 0已结束");
        }
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BizException("券模板不存在");
        }
        coupon.setStatus(status.byteValue());
        couponMapper.updateById(coupon);
    }

    // ==================== 领券中心 / 我的优惠券 ====================

    /** 领券中心列表：进行中 + 未过期 + 未领完（附每人剩余可领数） */
    public Page<Map<String, Object>> portalPage(Long memberId, long page, long size) {
        Page<Coupon> couponPage = couponMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .gt(Coupon::getUseEndTime, LocalDateTime.now())
                        .apply("received_count < total_count")
                        .orderByDesc(Coupon::getCreateTime));
        return toPage(couponPage, memberId);
    }

    /** 领券：SETNX 幂等防重复提交 + DB 条件更新防超领 + per_limit 限领校验 */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long memberId, Long couponId) {
        String onceKey = RECEIVE_ONCE_KEY + memberId + ":" + couponId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(onceKey, "1", Duration.ofSeconds(RECEIVE_ONCE_TTL_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException("操作太频繁，请稍后再试");
        }
        try {
            Coupon coupon = couponMapper.selectById(couponId);
            if (coupon == null || coupon.getStatus() != 1) {
                throw new BizException("优惠券不存在或已结束");
            }
            if (coupon.getUseStartTime().isAfter(LocalDateTime.now())) {
                throw new BizException("优惠券尚未开始领取");
            }
            if (coupon.getUseEndTime().isBefore(LocalDateTime.now())) {
                throw new BizException("优惠券已过期");
            }
            // 每人限领校验（并发下以幂等键 + 插入兜底，练习项目可接受）
            Long received = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                    .eq(CouponUser::getCouponId, couponId)
                    .eq(CouponUser::getMemberId, memberId));
            if (received >= coupon.getPerLimit()) {
                throw new BizException("已达每人限领数量");
            }
            // 原子条件更新防超领：received_count < total_count 才自增（并发抢券唯一入口）
            int rows = couponMapper.update(null, new UpdateWrapper<Coupon>()
                    .eq("id", couponId)
                    .eq("status", 1)
                    .apply("received_count < total_count")
                    .setSql("received_count = received_count + 1"));
            if (rows == 0) {
                throw new BizException("手慢了，优惠券已被领完");
            }
            CouponUser couponUser = new CouponUser();
            couponUser.setCouponId(couponId);
            couponUser.setMemberId(memberId);
            couponUser.setStatus((byte) 0);
            couponUser.setReceiveTime(LocalDateTime.now());
            couponUserMapper.insert(couponUser);
            // 事务提交后释放幂等键（提交前释放会放大并发窗口：双请求同时通过 per_limit 校验造成超发）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.delete(onceKey);
                }
            });
        } catch (Exception e) {
            // 失败立即释放，避免幂等键残留卡住再次领取
            redisTemplate.delete(onceKey);
            throw e;
        }
    }

    /** 我的优惠券（status 筛选：0未使用 1已锁定 2已使用 3已过期；附券信息与使用订单号） */
    public Page<Map<String, Object>> mine(Long memberId, Integer status, long page, long size) {
        Page<CouponUser> userPage = couponUserMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CouponUser>()
                        .eq(CouponUser::getMemberId, memberId)
                        .eq(status != null, CouponUser::getStatus, status)
                        .orderByDesc(CouponUser::getReceiveTime));
        // 批量反查使用订单号（coupon_user 只存 order_id，不冗余 order_sn）
        Map<Long, String> orderSnMap = new java.util.HashMap<>();
        List<Long> orderIds = userPage.getRecords().stream()
                .map(CouponUser::getOrderId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!orderIds.isEmpty()) {
            ordersMapper.selectBatchIds(orderIds)
                    .forEach(o -> orderSnMap.put(o.getId(), o.getOrderSn()));
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (CouponUser cu : userPage.getRecords()) {
            Coupon coupon = couponMapper.selectById(cu.getCouponId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("couponUserId", cu.getId());
            row.put("couponId", cu.getCouponId());
            row.put("name", coupon == null ? "" : coupon.getName());
            row.put("type", coupon == null ? null : coupon.getType());
            row.put("amount", coupon == null ? null : coupon.getAmount());
            row.put("threshold", coupon == null ? null : coupon.getThreshold());
            row.put("status", cu.getStatus());
            row.put("receiveTime", cu.getReceiveTime());
            row.put("lockTime", cu.getLockTime());
            row.put("useTime", cu.getUseTime());
            row.put("orderId", cu.getOrderId());
            row.put("orderSn", cu.getOrderId() == null ? null : orderSnMap.get(cu.getOrderId()));
            row.put("useEndTime", coupon == null ? null : coupon.getUseEndTime());
            data.add(row);
        }
        Page<Map<String, Object>> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(data);
        return result;
    }

    // ==================== 内部契约（order 下单链路 / portal 结算预览） ====================

    /** 可用券（未使用 + 未过期 + 门槛达标），并计算每张券在 totalAmount 下的可抵金额 */
    public List<CouponAvailableDTO> getAvailableCoupons(Long memberId, BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<CouponUser> users = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getMemberId, memberId)
                .eq(CouponUser::getStatus, 0));
        List<CouponAvailableDTO> result = new ArrayList<>();
        for (CouponUser cu : users) {
            Coupon coupon = couponMapper.selectById(cu.getCouponId());
            if (coupon == null || coupon.getStatus() != 1) {
                continue;
            }
            if (coupon.getUseStartTime().isAfter(LocalDateTime.now())
                    || coupon.getUseEndTime().isBefore(LocalDateTime.now())) {
                continue;
            }
            if (totalAmount.compareTo(coupon.getThreshold()) < 0) {
                continue;
            }
            CouponAvailableDTO dto = new CouponAvailableDTO();
            dto.setCouponUserId(cu.getId());
            dto.setCouponId(coupon.getId());
            dto.setName(coupon.getName());
            dto.setType(coupon.getType());
            dto.setAmount(coupon.getAmount());
            dto.setThreshold(coupon.getThreshold());
            dto.setDiscountAmount(calcDiscount(coupon, totalAmount));
            result.add(dto);
        }
        return result;
    }

    /** 锁券：0→1（下单占用；校验归属与有效期，幂等：同订单已锁定直接成功） */
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long couponUserId, Long memberId, Long orderId) {
        CouponUser cu = couponUserMapper.selectById(couponUserId);
        if (cu == null || !cu.getMemberId().equals(memberId)) {
            throw new BizException("优惠券不存在");
        }
        if (cu.getStatus() == 1 && orderId.equals(cu.getOrderId())) {
            return; // 幂等：同一订单重复锁券直接成功（下单重试场景）
        }
        if (cu.getStatus() != 0) {
            throw new BizException("优惠券状态异常，无法使用");
        }
        Coupon coupon = couponMapper.selectById(cu.getCouponId());
        if (coupon == null || coupon.getStatus() != 1 || coupon.getUseEndTime().isBefore(LocalDateTime.now())) {
            throw new BizException("优惠券已过期或已结束");
        }
        // 条件更新：并发下单同一张券只有一个能锁成功（兼容 Seata AT 回滚）；写 order_id 供取消/退款反查
        int rows = couponUserMapper.update(null, new UpdateWrapper<CouponUser>()
                .eq("id", couponUserId)
                .eq("status", 0)
                .set("status", 1)
                .set("order_id", orderId)
                .set("lock_time", LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException("优惠券已被占用，请刷新后重试");
        }
    }

    /** 退券：1→0（取消订单/超时关单回退，按订单反查；过期置 3；幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long orderId, Long memberId) {
        List<CouponUser> locked = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getOrderId, orderId)
                .eq(CouponUser::getMemberId, memberId)
                .eq(CouponUser::getStatus, 1));
        for (CouponUser cu : locked) {
            int target = isExpired(cu.getCouponId()) ? 3 : 0;
            couponUserMapper.update(null, new UpdateWrapper<CouponUser>()
                    .eq("id", cu.getId())
                    .eq("status", 1)
                    .set("status", target)
                    .set("order_id", null)
                    .set("lock_time", null));
        }
    }

    /** 核销：1→2（支付成功确认核销，按订单反查；幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void use(Long orderId, Long memberId) {
        List<CouponUser> locked = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getOrderId, orderId)
                .eq(CouponUser::getMemberId, memberId)
                .eq(CouponUser::getStatus, 1));
        for (CouponUser cu : locked) {
            couponUserMapper.update(null, new UpdateWrapper<CouponUser>()
                    .eq("id", cu.getId())
                    .eq("status", 1)
                    .set("status", 2)
                    .set("use_time", LocalDateTime.now()));
        }
    }

    /** 退款退券：2→0（整单退款成功后退回，按订单反查；过期置 3；幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long orderId, Long memberId) {
        List<CouponUser> used = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getOrderId, orderId)
                .eq(CouponUser::getMemberId, memberId)
                .eq(CouponUser::getStatus, 2));
        for (CouponUser cu : used) {
            int target = isExpired(cu.getCouponId()) ? 3 : 0;
            couponUserMapper.update(null, new UpdateWrapper<CouponUser>()
                    .eq("id", cu.getId())
                    .eq("status", 2)
                    .set("status", target)
                    .set("order_id", null)
                    .set("use_time", null));
        }
    }

    /** 过期扫描：未使用且券已过有效期 → 置 3（兜底：领了不用的券定期清理） */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void expireTask() {
        List<CouponUser> pending = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getStatus, 0));
        int count = 0;
        for (CouponUser cu : pending) {
            if (isExpired(cu.getCouponId())) {
                couponUserMapper.update(null, new UpdateWrapper<CouponUser>()
                        .eq("id", cu.getId())
                        .eq("status", 0)
                        .set("status", 3));
                count++;
            }
        }
        if (count > 0) {
            log.info("优惠券过期扫描完成，共置过期 {} 张", count);
        }
    }

    // ==================== 内部 ====================

    /** 计算单张券在 totalAmount 下的可抵金额：满减=面值；折扣=(1-折扣率)*总额，封顶不超过总额 */
    private BigDecimal calcDiscount(Coupon coupon, BigDecimal totalAmount) {
        if (coupon.getType() == 1) {
            return coupon.getAmount();
        }
        // 折扣券：如 0.85 折 → 优惠 15%，封顶不超过商品总额
        BigDecimal rate = coupon.getAmount();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = totalAmount.multiply(BigDecimal.ONE.subtract(rate))
                .setScale(2, RoundingMode.HALF_UP);
        return discount.min(totalAmount);
    }

    private boolean isExpired(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        return coupon == null || coupon.getUseEndTime().isBefore(LocalDateTime.now());
    }

    private Page<Map<String, Object>> toPage(Page<Coupon> source, Long memberId) {
        List<Map<String, Object>> data = source.getRecords().stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("type", c.getType());
            row.put("amount", c.getAmount());
            row.put("threshold", c.getThreshold());
            row.put("totalCount", c.getTotalCount());
            row.put("perLimit", c.getPerLimit());
            row.put("receivedCount", c.getReceivedCount());
            row.put("useStartTime", c.getUseStartTime());
            row.put("useEndTime", c.getUseEndTime());
            row.put("status", c.getStatus());
            // 每人剩余可领数
            long received = memberId == null ? 0 : couponUserMapper.selectCount(
                    new LambdaQueryWrapper<CouponUser>()
                            .eq(CouponUser::getCouponId, c.getId())
                            .eq(CouponUser::getMemberId, memberId));
            row.put("remainPerUser", Math.max(0, c.getPerLimit() - received));
            return row;
        }).toList();
        Page<Map<String, Object>> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(data);
        return result;
    }
}
