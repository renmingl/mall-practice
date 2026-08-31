package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.api.order.CommentValidateResult;
import com.mall.api.order.OrderFeignClient;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductComment;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductCommentMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import com.mall.product.dto.CommentCreateDTO;
import com.mall.product.util.ProductJsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品评价：发表评价（order 校验订单项归属+订单已完成+未评价）、商品评价列表、我的评价、后台管理
 * 防重复评价双保险：order 侧校验 + product_comment.uk_order_item_id 唯一键（并发兜底）
 * @author renmingl
 * @date 2026-08-31 10:00:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final ProductCommentMapper commentMapper;
    private final ProductSpuMapper spuMapper;
    private final OrderFeignClient orderFeignClient;

    /** 发表评价：调 order 校验订单项（归属会员 + 订单已完成 + 未评价） */
    @Transactional(rollbackFor = Exception.class)
    public void create(Long memberId, CommentCreateDTO dto) {
        CommentValidateResult validate = orderFeignClient.validateCommentable(dto.getOrderItemId(), memberId)
                .getDataOrThrow();
        if (!Boolean.TRUE.equals(validate.getCanComment())) {
            throw new BizException(validate.getReason());
        }
        ProductComment comment = new ProductComment();
        comment.setOrderItemId(dto.getOrderItemId());
        comment.setOrderSn(validate.getOrderSn());
        comment.setMemberId(memberId);
        comment.setSpuId(validate.getSpuId());
        comment.setSkuId(validate.getSkuId());
        comment.setRating(dto.getRating());
        comment.setContent(dto.getContent());
        // pics 逗号分隔列表 → JSON 数组列
        comment.setPics(ProductJsonUtil.wrapPics(dto.getPics() == null ? null : String.join(",", dto.getPics())));
        comment.setStatus((byte) 1);
        try {
            commentMapper.insert(comment);
        } catch (DuplicateKeyException e) {
            throw new BizException("该订单项已评价，请勿重复评价");
        }
    }

    /** 商品评价分页（仅显示正常状态，按时间倒序） */
    public Page<Map<String, Object>> pageBySpu(Long spuId, long page, long size) {
        Page<ProductComment> commentPage = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductComment>()
                        .eq(ProductComment::getSpuId, spuId)
                        .eq(ProductComment::getStatus, 1)
                        .orderByDesc(ProductComment::getCreateTime));
        return toPage(commentPage);
    }

    /** 我的评价分页 */
    public Page<Map<String, Object>> pageMine(Long memberId, long page, long size) {
        Page<ProductComment> commentPage = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductComment>()
                        .eq(ProductComment::getMemberId, memberId)
                        .orderByDesc(ProductComment::getCreateTime));
        return toPage(commentPage);
    }

    /** 后台评价分页（全部状态，支持商品名称/会员筛选） */
    public Page<Map<String, Object>> adminPage(String keyword, Integer status, long page, long size) {
        // keyword 按 SPU 名称反查 spuId 列表（product_comment 无 spu_name 列，避免冗余）
        List<Long> spuIds = null;
        if (StringUtils.hasText(keyword)) {
            spuIds = spuMapper.selectList(new LambdaQueryWrapper<ProductSpu>()
                            .like(ProductSpu::getName, keyword))
                    .stream().map(ProductSpu::getId).toList();
            if (spuIds.isEmpty()) {
                spuIds = List.of(-1L);
            }
        }
        Page<ProductComment> commentPage = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductComment>()
                        .in(spuIds != null, ProductComment::getSpuId, spuIds)
                        .eq(status != null, ProductComment::getStatus, status)
                        .orderByDesc(ProductComment::getCreateTime));
        return toPage(commentPage);
    }

    /** 商家回复 */
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, String reply) {
        ProductComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException("评价不存在");
        }
        comment.setReply(reply);
        comment.setReplyTime(LocalDateTime.now());
        commentMapper.updateById(comment);
    }

    /** 隐藏/显示 */
    public void updateStatus(Long id, Integer status) {
        ProductComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException("评价不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态仅支持 0隐藏 / 1正常");
        }
        comment.setStatus(status.byteValue());
        commentMapper.updateById(comment);
    }

    private Page<Map<String, Object>> toPage(Page<ProductComment> source) {
        // 批量反查 SPU 名称（product_comment 无冗余列）
        Map<Long, String> spuNames = new java.util.HashMap<>();
        List<Long> spuIds = source.getRecords().stream().map(ProductComment::getSpuId).distinct().toList();
        if (!spuIds.isEmpty()) {
            for (ProductSpu spu : spuMapper.selectBatchIds(spuIds)) {
                spuNames.put(spu.getId(), spu.getName());
            }
        }
        List<Map<String, Object>> data = source.getRecords().stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("orderItemId", c.getOrderItemId());
            row.put("orderSn", c.getOrderSn());
            row.put("memberId", c.getMemberId());
            row.put("spuId", c.getSpuId());
            row.put("skuId", c.getSkuId());
            row.put("spuName", spuNames.getOrDefault(c.getSpuId(), ""));
            row.put("rating", c.getRating());
            row.put("content", c.getContent());
            row.put("pics", ProductJsonUtil.unwrapPics(c.getPics()));
            row.put("status", c.getStatus());
            row.put("reply", c.getReply());
            row.put("replyTime", c.getReplyTime());
            row.put("createTime", c.getCreateTime());
            return row;
        }).toList();
        Page<Map<String, Object>> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(data);
        return result;
    }
}
