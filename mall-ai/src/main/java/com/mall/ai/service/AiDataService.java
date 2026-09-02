package com.mall.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.ai.model.AiUser;
import com.mall.api.cart.CartFeignClient;
import com.mall.api.coupon.CouponFeignClient;
import com.mall.api.member.MemberFeignClient;
import com.mall.api.order.OrderFeignClient;
import com.mall.api.product.ProductFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 数据问答供给（阶段 9 16.3）：登录态能力分层 + 意图路由 + Feign 按需取数
 * 只按需查（命中意图才调 internal 契约），不落业务数据；单服务故障不阻断对话（降级为知识问答）
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDataService {

    /** 拼入 system 提示的数据上下文最大长度（防超长 JSON 烧 token） */
    private static final int MAX_DATA_LENGTH = 4000;

    private final AiKnowledgeService knowledgeService;
    private final MemberFeignClient memberFeignClient;
    private final OrderFeignClient orderFeignClient;
    private final CouponFeignClient couponFeignClient;
    private final ProductFeignClient productFeignClient;
    private final CartFeignClient cartFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 组装 system 提示：身份 + 能力边界 + （知识块 / 数据上下文）
     */
    public String buildSystemPrompt(AiUser user, String scene, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 mall-practice 电商微服务学习项目的 AI 助手（项目定位：Spring Cloud Alibaba + Vue3 电商学习项目）。")
                .append("回答使用中文，简明准确；不知道的如实说明，不要编造。");
        if (user == null || !user.isLoggedIn()) {
            sb.append("当前为游客（未登录）：只能回答通用问题与项目知识，不能查询任何业务数据；");
        } else if (user.isAdmin()) {
            sb.append("当前用户为后台管理员：可查询管理侧数据（今日订单/趋势/库存预警/销量排行/会员运营），不能查询买家个人数据；");
        } else {
            sb.append("当前用户为登录买家：可查询本人数据（优惠券/积分/等级/收货地址/最近订单/购物车），不能查询其他用户或管理侧数据；");
        }
        // 项目知识检索（Top-3 命中块）
        List<String> knowledge = knowledgeService.retrieve(question, 3);
        if (!knowledge.isEmpty()) {
            sb.append("【项目知识参考】").append(String.join("；", knowledge)).append("；");
        }
        // 数据供给（意图命中才查）
        String data = collectData(user, question);
        if (data != null) {
            sb.append("【实时业务数据（回答必须基于以下数据，只许转述与解释，勿编造不存在的字段）】").append(data);
        } else {
            sb.append("【未查询到相关业务数据，按知识或常识回答】");
        }
        return sb.toString();
    }

    /** 意图路由：命中 → 取数并序列化 JSON；未命中/失败 → null（不阻断对话） */
    private String collectData(AiUser user, String question) {
        if (user == null || !user.isLoggedIn()) {
            return null;
        }
        List<Map<String, Object>> collected = new ArrayList<>();
        if (user.isAdmin()) {
            collectAdmin(user.userId(), question, collected);
        } else {
            collectMember(user.userId(), question, collected);
        }
        if (collected.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(collected);
            if (json.length() > MAX_DATA_LENGTH) {
                json = json.substring(0, MAX_DATA_LENGTH) + "…（截断）";
            }
            return json;
        } catch (Exception e) {
            log.error("AI data serialize failed", e);
            return null;
        }
    }

    /** 买家侧数据（本人维度）：券 / 账户（积分等级地址）/ 最近订单 / 购物车 */
    private void collectMember(Long memberId, String question, List<Map<String, Object>> collected) {
        if (containsAny(question, "优惠券", "可用券", "我的券")) {
            safeFetch(collected, "优惠券", () -> couponFeignClient.mineCoupons(memberId).getDataOrThrow());
        }
        if (containsAny(question, "积分", "等级", "账户", "资料", "地址", "收货")) {
            safeFetch(collected, "账户概览", () -> memberFeignClient.accountOverview(memberId).getDataOrThrow());
        }
        if (containsAny(question, "订单", "买过", "待发货", "待收货")) {
            safeFetch(collected, "最近订单", () -> orderFeignClient.recentOrders(memberId, 5).getDataOrThrow());
        }
        if (containsAny(question, "购物车")) {
            safeFetch(collected, "购物车", () -> cartFeignClient.getCheckedItems(memberId).getDataOrThrow());
        }
    }

    /** 管理员侧数据（全局维度）：今日订单 / 趋势 / 库存预警 / 销量排行 / 会员运营 */
    private void collectAdmin(Long adminId, String question, List<Map<String, Object>> collected) {
        if (containsAny(question, "今日订单", "今日销售", "销售额", "订单数", "销售")) {
            safeFetch(collected, "今日订单概览", () -> orderFeignClient.todayStats().getDataOrThrow());
        }
        if (containsAny(question, "趋势", "近7天", "近七天", "最近7天", "走势")) {
            safeFetch(collected, "近7天订单趋势", () -> orderFeignClient.trend7d().getDataOrThrow());
        }
        if (containsAny(question, "库存", "预警", "缺货", "补货")) {
            safeFetch(collected, "库存预警", () -> productFeignClient.stockWarnings().getDataOrThrow());
        }
        if (containsAny(question, "销量", "排行", "热销", "畅销")) {
            safeFetch(collected, "销量排行", () -> productFeignClient.salesRank(10).getDataOrThrow());
        }
        if (containsAny(question, "浏览", "pv", "uv", "PV", "UV", "TOP", "top")) {
            safeFetch(collected, "浏览Top", () -> productFeignClient.topViews(10).getDataOrThrow());
        }
        if (containsAny(question, "会员", "用户数", "在线", "新增", "签到", "日活", "运营")) {
            safeFetch(collected, "会员运营", () -> memberFeignClient.statsSummary().getDataOrThrow());
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 单服务故障降级：记录日志跳过该意图，其余数据照常返回 */
    private void safeFetch(List<Map<String, Object>> collected, String intent, Fetcher fetcher) {
        try {
            Object data = fetcher.fetch();
            if (data == null) {
                return;
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("intent", intent);
            row.put("data", data);
            collected.add(row);
        } catch (Exception e) {
            log.warn("AI data fetch failed: intent={}, err={}", intent, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Fetcher {
        Object fetch();
    }
}
