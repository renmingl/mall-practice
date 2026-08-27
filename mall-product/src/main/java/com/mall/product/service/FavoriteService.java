package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.MemberFavorite;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.MemberFavoriteMapper;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品收藏服务（场景 2.7）：收藏/取消/列表，uk_member_spu 唯一键防重复
 * @author renmingl
 * @date 2026-08-27 10:31:00
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final MemberFavoriteMapper favoriteMapper;
    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;

    /** 收藏（幂等：已收藏直接成功；唯一键 uk_member_spu 兜底） */
    @Transactional(rollbackFor = Exception.class)
    public void add(Long memberId, Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null || spu.getStatus() == 0) {
            throw new BizException("商品不存在或已下架");
        }
        long existCount = favoriteMapper.selectCount(new LambdaQueryWrapper<MemberFavorite>()
                .eq(MemberFavorite::getMemberId, memberId)
                .eq(MemberFavorite::getSpuId, spuId));
        if (existCount > 0) {
            return;
        }
        MemberFavorite favorite = new MemberFavorite();
        favorite.setMemberId(memberId);
        favorite.setSpuId(spuId);
        favoriteMapper.insert(favorite);
    }

    public void remove(Long memberId, Long spuId) {
        favoriteMapper.delete(new LambdaQueryWrapper<MemberFavorite>()
                .eq(MemberFavorite::getMemberId, memberId)
                .eq(MemberFavorite::getSpuId, spuId));
    }

    /** 我的收藏分页（附商品名称/主图/最低售价） */
    public Page<Map<String, Object>> list(Long memberId, long page, long size) {
        Page<MemberFavorite> favoritePage = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MemberFavorite>()
                        .eq(MemberFavorite::getMemberId, memberId)
                        .orderByDesc(MemberFavorite::getCreateTime));
        List<Long> spuIds = favoritePage.getRecords().stream().map(MemberFavorite::getSpuId).distinct().toList();
        Map<Long, ProductSpu> spuMap = new java.util.HashMap<>();
        Map<Long, java.math.BigDecimal> minPriceMap = new java.util.HashMap<>();
        if (!spuIds.isEmpty()) {
            for (ProductSpu spu : spuMapper.selectBatchIds(spuIds)) {
                spuMap.put(spu.getId(), spu);
            }
            // 批量查 SKU 最低售价（收藏列表展示用）
            skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                            .in(ProductSku::getSpuId, spuIds)
                            .eq(ProductSku::getStatus, 1))
                    .forEach(sku -> minPriceMap.merge(sku.getSpuId(), sku.getPrice(),
                            (a, b) -> a.compareTo(b) <= 0 ? a : b));
        }
        List<Map<String, Object>> data = favoritePage.getRecords().stream().map(f -> {
            ProductSpu spu = spuMap.get(f.getSpuId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("favoriteId", f.getId());
            row.put("spuId", f.getSpuId());
            row.put("createTime", f.getCreateTime());
            if (spu != null) {
                row.put("name", spu.getName());
                row.put("subtitle", spu.getSubtitle());
                row.put("mainPic", spu.getMainPic());
                row.put("price", minPriceMap.get(spu.getId()));
            }
            return row;
        }).collect(Collectors.toList());
        Page<Map<String, Object>> result = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        result.setRecords(data);
        return result;
    }

    /** 是否已收藏 */
    public boolean isFavorite(Long memberId, Long spuId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<MemberFavorite>()
                .eq(MemberFavorite::getMemberId, memberId)
                .eq(MemberFavorite::getSpuId, spuId)) > 0;
    }
}
