package com.mall.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.api.cart.CartFeignClient;
import com.mall.api.cart.CartItemDTO;
import com.mall.api.coupon.CouponAvailableDTO;
import com.mall.api.coupon.CouponFeignClient;
import com.mall.api.order.CommentValidateResult;
import com.mall.api.order.OrderInfoDTO;
import com.mall.api.order.OrderItemInfoDTO;
import com.mall.api.payment.CreatePaymentDTO;
import com.mall.api.payment.PaymentDTO;
import com.mall.api.payment.PaymentFeignClient;
import com.mall.api.product.DeductStockDTO;
import com.mall.api.product.ProductFeignClient;
import com.mall.api.product.ReleaseStockDTO;
import com.mall.api.product.SkuOrderInfoDTO;
import com.mall.api.seckill.SeckillVerifyResultDTO;
import com.mall.common.exception.BizException;
import com.mall.common.mq.MqSender;
import com.mall.common.mq.MqTopics;
import com.mall.mbg.entity.OrderItem;
import com.mall.mbg.entity.OrderStatusLog;
import com.mall.mbg.entity.Orders;
import com.mall.mbg.mapper.OrderItemMapper;
import com.mall.mbg.mapper.OrderStatusLogMapper;
import com.mall.mbg.mapper.OrdersMapper;
import com.mall.order.dto.OrderCreateDTO;
import com.mall.order.dto.SeckillOrderMsg;
import com.mall.order.remote.SeckillRemoteService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务：下单编排（全局事务）/取消/支付拉起/状态流转/超时关单/自动收货/后台发货/内部契约
 * 下单编排（@GlobalTransactional）：requestId 幂等 → 购物车勾选条目 → SKU 快照校验 →
 * 金额计算 → 锁券 → 插单+明细快照+流水 → 扣库存 → 清购物车 → 事务提交后发延迟关单消息
 * 取消/关单回补：先条件更新 0→4（幂等），再同步退券 + 回补库存（远程幂等，失败抛异常回滚本地订单状态）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 超时关单窗口（分钟）：与延迟消息 30 分钟一致 */
    public static final int CLOSE_MINUTES = 30;

    /** 超时自动收货窗口（天）：发货后 15 天未确认自动收货 */
    public static final int AUTO_RECEIVE_DAYS = 15;

    /** 秒杀结果 key 前缀（与 mall-seckill SeckillConstants.KEY_RESULT 约定一致）：seckill:result:{memberId}:{pid} */
    private static final String SECKILL_RESULT_KEY_PREFIX = "seckill:result:";

    /** 秒杀订单映射 key 前缀：seckill:order:{orderSn}（关单兑底扫描需按订单号反查秒杀商品） */
    private static final String SECKILL_ORDER_MAPPING_PREFIX = "seckill:order:";

    /** 秒杀订单映射 TTL：覆盖关单窗口（30 分钟）+ 兑底扫描余量 */
    private static final Duration SECKILL_MAPPING_TTL = Duration.ofHours(48);

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final OrderStatusService statusService;
    private final CartFeignClient cartFeignClient;
    private final ProductFeignClient productFeignClient;
    private final CouponFeignClient couponFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final MqSender mqSender;
    private final SeckillRemoteService seckillRemoteService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 自身代理：closeExpiredOrders 经此调用，使 @Transactional 事务注解生效（同类直调绕过代理）；@Lazy 避免初始化循环依赖 */
    @Autowired
    @Lazy
    private OrderService self;

    // ==================== 下单编排 ====================

    /**
     * 下单：全局事务编排（order 发起，product 扣库存 / coupon 锁券为参与方）。
     * 幂等：同 requestId 已存在订单直接返回（防重复提交）
     */
    @GlobalTransactional(rollbackFor = Exception.class)
    @Transactional(rollbackFor = Exception.class)
    public Orders createOrder(Long memberId, OrderCreateDTO dto) {
        // 1. 幂等：重复提交直接返回已建订单
        Orders exist = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getRequestId, dto.getRequestId()));
        if (exist != null) {
            log.info("重复下单请求，直接返回已有订单 requestId={} orderSn={}", dto.getRequestId(), exist.getOrderSn());
            return exist;
        }
        // 2. 购物车勾选条目（服务端拉取，不信任前端金额）
        List<CartItemDTO> cartItems = cartFeignClient.getCheckedItems(memberId).getDataOrThrow();
        if (cartItems.isEmpty()) {
            throw new BizException("请先勾选要结算的商品");
        }
        List<Long> skuIds = cartItems.stream().map(CartItemDTO::getSkuId).toList();
        // 3. SKU 快照（下架/停用商品也返回，由本服务校验拦截）
        List<SkuOrderInfoDTO> skus = productFeignClient.getSkuOrderInfos(skuIds).getDataOrThrow();
        Map<Long, SkuOrderInfoDTO> skuMap = skus.stream()
                .collect(Collectors.toMap(SkuOrderInfoDTO::getSkuId, Function.identity()));
        // 4. 逐条校验 + 商品总额
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItemDTO item : cartItems) {
            SkuOrderInfoDTO sku = skuMap.get(item.getSkuId());
            if (sku == null || sku.getStatus() != 1 || sku.getSpuStatus() != 1) {
                throw new BizException("商品已下架或停用，请重新选购");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BizException("购买数量不合法");
            }
            if (sku.getStock() < item.getQuantity()) {
                throw new BizException("商品库存不足：" + sku.getSpuName());
            }
            totalAmount = totalAmount.add(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        // 5. 优惠券：从可用券中校验并取可抵金额（锁券在插单后进行）
        BigDecimal couponAmount = BigDecimal.ZERO;
        Long couponUserId = dto.getCouponUserId();
        if (couponUserId != null) {
            List<CouponAvailableDTO> available = couponFeignClient
                    .getAvailableCoupons(memberId, totalAmount).getDataOrThrow();
            CouponAvailableDTO picked = available.stream()
                    .filter(c -> c.getCouponUserId().equals(couponUserId))
                    .findFirst()
                    .orElseThrow(() -> new BizException("优惠券不可用（未领取/已过期/未达门槛）"));
            couponAmount = picked.getDiscountAmount();
        }
        BigDecimal payAmount = totalAmount.subtract(couponAmount).max(BigDecimal.ZERO);
        // 6. 插入订单（status=0 待付款）
        Orders order = new Orders();
        order.setOrderSn(generateOrderSn());
        order.setRequestId(dto.getRequestId());
        order.setMemberId(memberId);
        order.setOrderType((byte) 1);
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setCouponAmount(couponAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(payAmount);
        order.setStatus((byte) 0);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        ordersMapper.insert(order);
        // 7. 订单明细快照
        for (CartItemDTO item : cartItems) {
            SkuOrderInfoDTO sku = skuMap.get(item.getSkuId());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderSn(order.getOrderSn());
            orderItem.setSpuId(sku.getSpuId());
            orderItem.setSpuName(sku.getSpuName());
            orderItem.setSkuId(sku.getSkuId());
            orderItem.setSkuCode(sku.getSkuCode());
            orderItem.setSpec(sku.getSpec());
            orderItem.setPic(sku.getPic());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItemMapper.insert(orderItem);
        }
        // 8. 状态流水：创建
        statusService.logCreate(order);
        // 9. 锁券（写入 coupon_user.order_id，取消/退款按订单反查）
        if (couponUserId != null) {
            couponFeignClient.lockCoupon(couponUserId, memberId, order.getId()).getDataOrThrow();
        }
        // 10. 扣库存（product 侧行锁 + 条件原子更新，防超卖）
        for (CartItemDTO item : cartItems) {
            DeductStockDTO dtoStock = new DeductStockDTO();
            dtoStock.setBizSn(order.getOrderSn());
            dtoStock.setSkuId(item.getSkuId());
            dtoStock.setQuantity(item.getQuantity());
            productFeignClient.deductStock(dtoStock).getDataOrThrow();
        }
        // 11. 清购物车已结算条目（失败上抛，全局事务回滚）
        cartFeignClient.removeChecked(memberId, skuIds).getDataOrThrow();
        // 12. 事务提交后发延迟关单消息（30 分钟未支付自动关单；失败由定时扫描兜底）
        String orderSn = order.getOrderSn();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mqSender.trySendDelay(MqTopics.ORDER_CLOSE, MqTopics.TAG_CLOSE,
                        Map.of("orderSn", orderSn), MqTopics.DELAY_LEVEL_30M);
            }
        });
        log.info("下单成功 orderSn={} memberId={} payAmount={}", orderSn, memberId, payAmount);
        return order;
    }

    /**
     * 秒杀落单（MQ 消费，14.5）：核验 Redis 预扣资格（防绕过秒杀入口）→ 插单（order_type=2）
     * → 扣 sku.stock（change_type=4）。秒杀链路不用 Seata（README 最终一致性）：
     * 核验失败/扣减失败均写 Redis 结果（status=2）并回滚本地订单；扣减失败经 seckill 回补
     * Redis 秒杀库存（活动进行中）/跳过回补（活动已结束且未扣过 sku.stock，防虚增）
     */
    @Transactional(rollbackFor = Exception.class)
    public Orders createSeckillOrder(SeckillOrderMsg msg) {
        // 1. 幂等：同 requestId 已存在订单直接返回（MQ 重试/重复消费场景）
        Orders exist = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getRequestId, msg.getRequestId()));
        if (exist != null) {
            log.info("秒杀重复落单请求，直接返回已有订单 requestId={} orderSn={}", msg.getRequestId(), exist.getOrderSn());
            return exist;
        }
        Long memberId = msg.getMemberId();
        Long pid = msg.getSeckillProductId();
        int quantity = msg.getQuantity() == null ? 1 : msg.getQuantity();
        // 2. 核验预扣资格（order → seckill Dubbo/Feign 双契约）；失败不建单，写失败结果供轮询
        SeckillVerifyResultDTO verify = seckillRemoteService.verifyReservation(pid, memberId, quantity);
        if (!verify.isOk()) {
            log.warn("秒杀核验未通过 memberId={} productId={} reason={}", memberId, pid, verify.getReason());
            writeSeckillResult(memberId, pid, 2, null, verify.getReason());
            return null;
        }
        // 3. 插单（order_type=2：秒杀订单，秒杀价 × 数量，无券/无运费）
        BigDecimal payAmount = verify.getSeckillPrice().multiply(BigDecimal.valueOf(quantity));
        Orders order = new Orders();
        order.setOrderSn(generateOrderSn());
        order.setRequestId(msg.getRequestId());
        order.setMemberId(memberId);
        order.setOrderType((byte) 2);
        order.setTotalAmount(payAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayAmount(payAmount);
        order.setStatus((byte) 0);
        order.setReceiverName(msg.getReceiverName());
        order.setReceiverPhone(msg.getReceiverPhone());
        order.setReceiverAddress(msg.getReceiverAddress());
        order.setRemark("秒杀订单");
        ordersMapper.insert(order);
        // 4. 明细快照（核验结果即秒杀快照）
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setOrderSn(order.getOrderSn());
        item.setSpuId(verify.getSpuId());
        item.setSpuName(verify.getSpuName());
        item.setSkuId(verify.getSkuId());
        item.setSkuCode(verify.getSkuCode());
        item.setSpec(verify.getSpec());
        item.setPic(verify.getPic());
        item.setPrice(verify.getSeckillPrice());
        item.setQuantity(quantity);
        item.setSubtotal(payAmount);
        orderItemMapper.insert(item);
        // 5. 状态流水
        statusService.logCreate(order);
        // 6. 扣 sku.stock（change_type=4，product 独立本地事务；秒杀链路不用 Seata，失败补偿后回滚）
        DeductStockDTO deduct = new DeductStockDTO();
        deduct.setBizSn(order.getOrderSn());
        deduct.setSkuId(verify.getSkuId());
        deduct.setQuantity(quantity);
        deduct.setChangeType(4);
        try {
            productFeignClient.deductStock(deduct).getDataOrThrow();
        } catch (Exception e) {
            log.error("秒杀落单扣库存失败 orderSn={} skuId={}", order.getOrderSn(), verify.getSkuId(), e);
            // 补偿：回补 Redis 秒杀库存（活动进行中）/ 已结束则按流水判断跳过 sku 回补；幂等 key 用 requestId（订单已回滚）
            try {
                seckillRemoteService.releaseSeckillStock(msg.getRequestId(), pid, verify.getSkuId(), quantity, memberId);
            } catch (Exception ex) {
                log.error("秒杀扣减失败补偿异常 requestId={}", msg.getRequestId(), ex);
            }
            writeSeckillResult(memberId, pid, 2, null, "库存扣减失败，订单已取消");
            throw new BizException("秒杀落单失败");
        }
        // 7. 写成功结果 + 订单映射（关单兑底扫描按订单号反查秒杀商品）
        writeSeckillResult(memberId, pid, 1, order.getOrderSn(), null);
        saveSeckillOrderMapping(order.getOrderSn(), pid, verify.getSkuId(), quantity);
        // 8. 事务提交后发延迟关单消息（30 分钟未支付自动关单；失败由定时扫描兑底）
        String orderSn = order.getOrderSn();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mqSender.trySendDelay(MqTopics.ORDER_CLOSE, MqTopics.TAG_CLOSE,
                        Map.of("orderSn", orderSn), MqTopics.DELAY_LEVEL_30M);
            }
        });
        log.info("秒杀落单成功 orderSn={} memberId={} quantity={}", orderSn, memberId, quantity);
        return order;
    }

    // ==================== 买家操作 ====================

    /** 取消订单（仅待付款可取消）：先条件更新 0→4，再回补券与库存 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long memberId, String orderSn) {
        Orders order = getOwnedOrder(orderSn, memberId);
        if (order.getStatus() != 0) {
            throw new BizException("当前状态不可取消");
        }
        boolean ok = statusService.cancel(order, "买家", "买家取消订单");
        if (!ok) {
            throw new BizException("订单状态已变化，请刷新后重试");
        }
        compensateCancel(order);
    }

    /** 确认收货：2→3 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long memberId, String orderSn) {
        Orders order = getOwnedOrder(orderSn, memberId);
        if (order.getStatus() != 2) {
            throw new BizException("当前状态不可确认收货");
        }
        if (!statusService.receive(order)) {
            throw new BizException("订单状态已变化，请刷新后重试");
        }
    }

    /** 拉起收银台：校验待付款后调 payment 创建支付流水（幂等：同订单+同方式复用流水） */
    public PaymentDTO pay(Long memberId, String orderSn, Byte payType) {
        Orders order = getOwnedOrder(orderSn, memberId);
        if (order.getStatus() != 0) {
            throw new BizException("订单当前状态不可支付");
        }
        if (payType == null || (payType != 1 && payType != 2)) {
            throw new BizException("支付方式仅支持 1支付宝 / 2微信");
        }
        CreatePaymentDTO dto = new CreatePaymentDTO();
        dto.setOrderId(order.getId());
        dto.setOrderSn(order.getOrderSn());
        dto.setMemberId(memberId);
        dto.setPayAmount(order.getPayAmount());
        dto.setPayType(payType);
        return paymentFeignClient.createPayment(dto).getDataOrThrow();
    }

    /** 订单详情（订单头 + 明细 + 状态流水） */
    public Map<String, Object> detail(Long memberId, String orderSn) {
        return buildDetail(getOwnedOrder(orderSn, memberId));
    }

    /** 我的订单分页（每单附明细，便于列表展示商品图） */
    public Page<Map<String, Object>> pageMine(Long memberId, Integer status, long page, long size) {
        Page<Orders> orderPage = ordersMapper.selectPage(new Page<>(page, size),
                Wrappers.<Orders>lambdaQuery()
                        .eq(Orders::getMemberId, memberId)
                        .eq(status != null, Orders::getStatus, status)
                        .orderByDesc(Orders::getCreateTime));
        return toOrderPage(orderPage);
    }

    // ==================== 后台管理 ====================

    /** 后台订单分页（按订单号/状态筛选） */
    public Page<Map<String, Object>> adminPage(String orderSn, Integer status, long page, long size) {
        Page<Orders> orderPage = ordersMapper.selectPage(new Page<>(page, size),
                Wrappers.<Orders>lambdaQuery()
                        .like(orderSn != null && !orderSn.isBlank(), Orders::getOrderSn, orderSn)
                        .eq(status != null, Orders::getStatus, status)
                        .orderByDesc(Orders::getCreateTime));
        return toOrderPage(orderPage);
    }

    /** 后台发货：1→2 + 物流信息 */
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long orderId, String company, String sn) {
        if (company == null || company.isBlank() || sn == null || sn.isBlank()) {
            throw new BizException("物流公司与单号必填");
        }
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!statusService.deliver(order, company, sn)) {
            throw new BizException("仅待发货订单可发货");
        }
    }

    // ==================== 内部契约（payment/product 调用） ====================

    /** 订单信息（payment 创建支付单/申请退款校验用） */
    public OrderInfoDTO getOrderInfo(String orderSn) {
        Orders order = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        OrderInfoDTO dto = new OrderInfoDTO();
        dto.setOrderId(order.getId());
        dto.setOrderSn(order.getOrderSn());
        dto.setMemberId(order.getMemberId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setFreightAmount(order.getFreightAmount());
        dto.setCouponAmount(order.getCouponAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setPayType(order.getPayType());
        dto.setStatus(order.getStatus());
        dto.setPayTime(order.getPayTime());
        dto.setDeliveryTime(order.getDeliveryTime());
        dto.setReceiveTime(order.getReceiveTime());
        return dto;
    }

    /** 订单项明细（payment 退款联动组装消息体，随消息投递给 product 回补库存） */
    public List<OrderItemInfoDTO> getOrderItems(String orderSn) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .eq(OrderItem::getOrderSn, orderSn))
                .stream()
                .map(item -> {
                    OrderItemInfoDTO dto = new OrderItemInfoDTO();
                    dto.setOrderItemId(item.getId());
                    dto.setSkuId(item.getSkuId());
                    dto.setQuantity(item.getQuantity());
                    dto.setPrice(item.getPrice());
                    return dto;
                })
                .toList();
    }

    /** 评价前校验：订单项存在 + 归属会员 + 订单已完成（未评价由 product 侧 uk_order_item_id 唯一键兜底） */
    public CommentValidateResult validateCommentable(Long orderItemId, Long memberId) {
        CommentValidateResult result = new CommentValidateResult();
        result.setOrderItemId(orderItemId);
        result.setMemberId(memberId);
        result.setCanComment(false);
        OrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null) {
            result.setReason("订单项不存在");
            return result;
        }
        Orders order = ordersMapper.selectById(item.getOrderId());
        if (order == null || !order.getMemberId().equals(memberId)) {
            result.setReason("订单不存在或不属于当前用户");
            return result;
        }
        if (order.getStatus() != 3) {
            result.setReason("订单未完成，暂不可评价");
            return result;
        }
        result.setOrderSn(order.getOrderSn());
        result.setSpuId(item.getSpuId());
        result.setSkuId(item.getSkuId());
        result.setSpuName(item.getSpuName());
        result.setSpec(item.getSpec());
        result.setPic(item.getPic());
        result.setCanComment(true);
        return result;
    }

    /** 支付成功回写：0→1 + 支付信息；成功后核销该订单锁定的优惠券（幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(String orderSn, Byte payType, String payTime) {
        Orders order = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() == 1) {
            return; // 幂等：已回写
        }
        if (order.getStatus() != 0) {
            throw new BizException("订单状态不可支付确认");
        }
        LocalDateTime paidTime = payTime == null || payTime.isBlank()
                ? LocalDateTime.now()
                : LocalDateTime.parse(payTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (!statusService.markPaid(order, payType, paidTime)) {
            throw new BizException("订单状态已变化，请刷新后重试");
        }
        // 核销券：按订单反查锁定券 1→2
        couponFeignClient.useCoupon(order.getId(), order.getMemberId());
        log.info("支付成功回写 orderSn={} payType={}", orderSn, payType);
    }

    /** 整单退款成功回写：1/2/3→5（幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void markRefunded(String orderSn) {
        Orders order = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() == 5) {
            return; // 幂等：已回写
        }
        if (order.getStatus() == 0 || order.getStatus() == 4) {
            throw new BizException("订单未支付或已取消，不可退款");
        }
        if (!statusService.markRefunded(order)) {
            throw new BizException("订单状态已变化，请刷新后重试");
        }
        log.info("整单退款回写 orderSn={}", orderSn);
    }

    // ==================== 超时关单 / 自动收货（MQ 消费 + 定时兜底共用） ====================

    /** 关单（延迟消息消费）：待付款且超时窗口已过 → 0→4 + 回补（同事务：回补失败回滚状态更新，MQ/定时重试时可重新执行） */
    @Transactional(rollbackFor = Exception.class)
    public boolean closeExpired(String orderSn) {
        Orders order = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrderSn, orderSn));
        if (order == null || order.getStatus() != 0) {
            return false; // 已支付/已取消/不存在，跳过
        }
        if (order.getCreateTime().plusMinutes(CLOSE_MINUTES).isAfter(LocalDateTime.now())) {
            return false; // 窗口未到（延迟消息提前到达场景）
        }
        if (!statusService.cancel(order, "系统", "超时未支付自动关单")) {
            return false;
        }
        log.info("超时关单 orderSn={}", orderSn);
        compensateCancel(order);
        return true;
    }

    /** 兜底扫描：全部超时未支付订单逐个关单（延迟消息丢失/失败场景） */
    public int closeExpiredOrders() {
        List<Orders> expired = ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, 0)
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(CLOSE_MINUTES))
                .last("LIMIT 200"));
        int count = 0;
        for (Orders order : expired) {
            try {
                // 经 self 代理调用：closeExpired 内部 status 条件更新（0→4）与回补同事务，补偿失败自动回滚重试
                if (self.closeExpired(order.getOrderSn())) {
                    count++;
                }
            } catch (Exception e) {
                log.error("兜底关单失败 orderSn={}", order.getOrderSn(), e);
            }
        }
        if (count > 0) {
            log.info("兜底关单完成 count={}", count);
        }
        return count;
    }

    /** 自动收货：发货超 15 天未确认 → 2→3 */
    public int autoReceive() {
        List<Orders> orders = ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, 2)
                .isNotNull(Orders::getDeliveryTime)
                .lt(Orders::getDeliveryTime, LocalDateTime.now().minusDays(AUTO_RECEIVE_DAYS))
                .last("LIMIT 200"));
        int count = 0;
        for (Orders order : orders) {
            if (statusService.receive(order)) {
                count++;
            }
        }
        if (count > 0) {
            log.info("超时自动收货完成 count={}", count);
        }
        return count;
    }

    // ==================== 运营数据（10.4，看板聚合） ====================

    /** 今日订单概览：今日订单数 / 已支付销售额（status 1/2/3）/ 秒杀订单数 */
    public Map<String, Object> todayStats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long orderCount = ordersMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .ge(Orders::getCreateTime, start));
        // 销售额按支付时间统计（status 1/2/3 已支付且未退款；退款单不纳入）
        List<Orders> paid = ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .ge(Orders::getPayTime, start)
                .in(Orders::getStatus, List.of((byte) 1, (byte) 2, (byte) 3)));
        BigDecimal salesAmount = paid.stream()
                .map(Orders::getPayAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long seckillCount = ordersMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .ge(Orders::getCreateTime, start)
                .eq(Orders::getOrderType, 2));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderCount", orderCount);
        row.put("salesAmount", salesAmount);
        row.put("seckillCount", seckillCount);
        return row;
    }

    /** 近 7 天订单趋势：每天订单数 + 已支付销售额（Java 分组，缺日补零） */
    public List<Map<String, Object>> trend7d() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(6).atStartOfDay();
        List<Orders> orders = ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .ge(Orders::getCreateTime, from));
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.format(DateTimeFormatter.ofPattern("MM-dd")));
            row.put("orderCount", 0);
            row.put("salesAmount", BigDecimal.ZERO);
            byDay.put(day.toString(), row);
        }
        for (Orders order : orders) {
            Map<String, Object> row = byDay.get(order.getCreateTime().toLocalDate().toString());
            if (row == null) {
                continue;
            }
            row.put("orderCount", (Integer) row.get("orderCount") + 1);
            if (order.getStatus() != null && order.getStatus() >= 1 && order.getStatus() <= 3
                    && order.getPayAmount() != null) {
                row.put("salesAmount", ((BigDecimal) row.get("salesAmount")).add(order.getPayAmount()));
            }
        }
        return new ArrayList<>(byDay.values());
    }

    // ==================== 私有 ====================

    /** 取消回补：秒杀订单走秒杀专用回补；普通订单退券（按订单反查）+ 回补库存（change_type=2，幂等） */
    private void compensateCancel(Orders order) {
        if (order.getOrderType() != null && order.getOrderType() == 2) {
            compensateSeckillCancel(order);
            return;
        }
        try {
            couponFeignClient.unlockCoupon(order.getId(), order.getMemberId());
            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderSn, order.getOrderSn()));
            for (OrderItem item : items) {
                ReleaseStockDTO dto = new ReleaseStockDTO();
                dto.setBizSn(order.getOrderSn());
                dto.setSkuId(item.getSkuId());
                dto.setQuantity(item.getQuantity());
                dto.setChangeType(2);
                productFeignClient.releaseStock(dto).getDataOrThrow();
            }
        } catch (Exception e) {
            // 远程回补均为幂等操作；失败抛异常回滚本地订单状态（订单回到待付款，重试时回补幂等跳过）
            log.error("取消订单回补失败 orderSn={}", order.getOrderSn(), e);
            throw new BizException("订单取消失败，请稍后重试");
        }
    }

    /**
     * 秒杀订单取消回补（14.6）：调 seckill 专用回补（活动进行中回补 Redis 秒杀库存，
     * 已结束回补 sku.stock change_type=9）；秒杀订单不用券，只回补库存
     */
    private void compensateSeckillCancel(Orders order) {
        try {
            Map<String, Object> mapping = readSeckillOrderMapping(order.getOrderSn());
            if (mapping == null) {
                // 映射缺失（Redis 过期/清理）：从订单明细取 skuId/quantity，pid 缺失则跳过（活动结束后库存对账兑底）
                log.warn("秒杀订单映射缺失 orderSn={}，跳过秒杀回补", order.getOrderSn());
                return;
            }
            Long pid = Long.valueOf(String.valueOf(mapping.get("seckillProductId")));
            Long skuId = Long.valueOf(String.valueOf(mapping.get("skuId")));
            int quantity = Integer.parseInt(String.valueOf(mapping.get("quantity")));
            seckillRemoteService.releaseSeckillStock(order.getOrderSn(), pid, skuId, quantity, order.getMemberId());
        } catch (Exception e) {
            // 回补幂等（seckill:released:{orderSn}）；失败抛异常回滚本地订单状态，重试时幂等跳过
            log.error("秒杀订单取消回补失败 orderSn={}", order.getOrderSn(), e);
            throw new BizException("订单取消失败，请稍后重试");
        }
    }

    /** 写秒杀结果（与 mall-seckill SeckillConstants.KEY_RESULT 约定一致，TTL 30 分钟供轮询） */
    private void writeSeckillResult(Long memberId, Long seckillProductId, int status, String orderSn, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        if (orderSn != null) {
            row.put("orderSn", orderSn);
        }
        if (reason != null) {
            row.put("reason", reason);
        }
        try {
            redisTemplate.opsForValue().set(SECKILL_RESULT_KEY_PREFIX + memberId + ":" + seckillProductId,
                    objectMapper.writeValueAsString(row), Duration.ofMinutes(30));
        } catch (Exception e) {
            log.error("写秒杀结果失败 memberId={} pid={}", memberId, seckillProductId, e);
        }
    }

    /** 保存秒杀订单映射（关单兑底扫描按订单号反查秒杀商品/数量） */
    private void saveSeckillOrderMapping(String orderSn, Long seckillProductId, Long skuId, int quantity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seckillProductId", seckillProductId);
        row.put("skuId", skuId);
        row.put("quantity", quantity);
        try {
            redisTemplate.opsForValue().set(SECKILL_ORDER_MAPPING_PREFIX + orderSn,
                    objectMapper.writeValueAsString(row), SECKILL_MAPPING_TTL);
        } catch (Exception e) {
            log.error("保存秒杀订单映射失败 orderSn={}", orderSn, e);
        }
    }

    private Map<String, Object> readSeckillOrderMapping(String orderSn) {
        try {
            String json = redisTemplate.opsForValue().get(SECKILL_ORDER_MAPPING_PREFIX + orderSn);
            return json == null ? null : objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("读取秒杀订单映射失败 orderSn={}", orderSn, e);
            return null;
        }
    }

    /** 查询并校验订单归属 */
    private Orders getOwnedOrder(String orderSn, Long memberId) {
        Orders order = ordersMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getOrderSn, orderSn));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getMemberId().equals(memberId)) {
            throw new BizException("无权操作该订单");
        }
        return order;
    }

    /** 订单分页转换：每单附明细（列表展示商品图） */
    private Page<Map<String, Object>> toOrderPage(Page<Orders> source) {
        List<Map<String, Object>> data = source.getRecords().stream().map(order -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", order);
            row.put("items", orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderSn, order.getOrderSn())));
            return row;
        }).toList();
        Page<Map<String, Object>> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(data);
        return result;
    }

    private Map<String, Object> buildDetail(Orders order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("order", order);
        map.put("items", orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderSn, order.getOrderSn())));
        map.put("statusLogs", statusLogMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderId, order.getId())
                .orderByAsc(OrderStatusLog::getId)));
        return map;
    }

    /** 订单号生成：O + yyyyMMddHHmmss + 4 位随机（uk_order_sn 兜底防撞） */
    private String generateOrderSn() {
        return "O" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
