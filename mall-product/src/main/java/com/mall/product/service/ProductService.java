package com.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.ProductBrand;
import com.mall.mbg.entity.ProductCategory;
import com.mall.mbg.entity.ProductSku;
import com.mall.mbg.entity.ProductSpu;
import com.mall.mbg.mapper.ProductBrandMapper;
import com.mall.mbg.mapper.ProductCategoryMapper;
import com.mall.mbg.mapper.ProductSkuMapper;
import com.mall.mbg.mapper.ProductSpuMapper;
import com.mall.product.dto.SpuSaveDTO;
import com.mall.product.util.ProductJsonUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品服务：SPU/SKU 维护、上下架、前台列表/详情、多级缓存（场景 2.2/2.3/2.4/2.5、13.3）
 * 多级缓存：L1 Caffeine 本地（60s 短 TTL）→ L2 Redis（随机 TTL）→ L3 DB；双清保一致
 * 缓存三防：穿透=空值短缓存；击穿=Redis SETNX 互斥锁；雪崩=TTL 随机偏移
 * @author renmingl
 * @date 2026-08-27 10:30:40
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String DETAIL_KEY = "product:detail:";
    private static final String LOCK_KEY = "product:detail:lock:";
    private static final String NULL_MARK = "NULL";
    private static final long LOCK_WAIT_MS = 3000L;
    private static final long LOCAL_TTL_SECONDS = 60L;
    private static final long LOCAL_MAX_SIZE = 5000L;

    /** L1 本地缓存（Caffeine 多级缓存 13.3）：热点详情短 TTL，以 L2 Redis 为准；增删改走 evictDetailCache 双清 */
    private final Cache<Long, Map<String, Object>> localCache = Caffeine.newBuilder()
            .maximumSize(LOCAL_MAX_SIZE)
            .expireAfterWrite(Duration.ofSeconds(LOCAL_TTL_SECONDS))
            .recordStats()
            .build();

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductBrandMapper brandMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mall.product.detail-cache-ttl:1800}")
    private long cacheTtl;
    @Value("${mall.product.detail-cache-ttl-jitter:300}")
    private long cacheTtlJitter;
    @Value("${mall.product.preload-top:20}")
    private int preloadTop;

    // ==================== 后台管理 ====================

    /** 后台商品分页（全部状态，支持编码/名称/分类/状态筛选） */
    public Page<ProductSpu> adminPage(String spuCode, String name, Long categoryId, Integer status, long page, long size) {
        return spuMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductSpu>()
                        .like(StringUtils.hasText(spuCode), ProductSpu::getSpuCode, spuCode)
                        .like(StringUtils.hasText(name), ProductSpu::getName, name)
                        .eq(categoryId != null, ProductSpu::getCategoryId, categoryId)
                        .eq(status != null, ProductSpu::getStatus, status)
                        .orderByDesc(ProductSpu::getCreateTime));
    }

    /** 商品详情（编辑回显）：SPU + 全部 SKU 列表（含停用，避免保存时误删停用 SKU）+ 分类/品牌名 */
    public Map<String, Object> adminDetail(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException("商品不存在");
        }
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .orderByAsc(ProductSku::getId));
        // 后台编辑回显：JSON 列解包为普通文本
        spu.setPics(ProductJsonUtil.unwrapPics(spu.getPics()));
        skus.forEach(sku -> sku.setSpec(ProductJsonUtil.unwrapText(sku.getSpec())));
        ProductCategory category = spu.getCategoryId() == null ? null : categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spu", spu);
        data.put("skuList", skus);
        data.put("categoryName", category == null ? null : category.getName());
        data.put("brandName", brand == null ? null : brand.getName());
        return data;
    }

    /** 新增/修改商品（SPU + SKU 全量重建，事务；修改时 SKU 库存以原值为准，库存只经入库/盘点变动） */
    @Transactional(rollbackFor = Exception.class)
    public void save(SpuSaveDTO dto) {
        if (!StringUtils.hasText(dto.getSpuCode()) || !StringUtils.hasText(dto.getName())) {
            throw new BizException("商品编码和名称必填");
        }
        if (dto.getCategoryId() == null) {
            throw new BizException("商品分类必填");
        }
        int spuStatus = dto.getStatus() == null ? 0 : dto.getStatus();
        if (spuStatus != 0 && spuStatus != 1) {
            throw new BizException("商品状态仅支持 0下架 / 1上架");
        }
        if (spuStatus == 1) {
            boolean hasEnabledSku = dto.getSkuList() != null && dto.getSkuList().stream()
                    .anyMatch(s -> s.getStatus() == null || s.getStatus() == 1);
            if (!hasEnabledSku) {
                throw new BizException("上架商品至少需要一个启用状态的 SKU");
            }
        }
        Map<Long, ProductSku> oldSkuMap = new HashMap<>();
        ProductSpu spu = new ProductSpu();
        spu.setId(dto.getId());
        spu.setSpuCode(dto.getSpuCode());
        spu.setCategoryId(dto.getCategoryId());
        spu.setBrandId(dto.getBrandId());
        spu.setName(dto.getName());
        spu.setSubtitle(dto.getSubtitle());
        spu.setMainPic(dto.getMainPic());
        // pics 为 JSON 数组列：逗号分隔 URL 包装为 JSON 后入库
        spu.setPics(ProductJsonUtil.wrapPics(dto.getPics()));
        spu.setUnit(dto.getUnit());
        spu.setDetail(dto.getDetail());
        spu.setStatus((byte) spuStatus);
        spu.setSort(dto.getSort() == null ? 0 : dto.getSort());

        if (spu.getId() == null) {
            spu.setSales(0);
            spuMapper.insert(spu);
        } else {
            ProductSpu exist = spuMapper.selectById(spu.getId());
            if (exist == null) {
                throw new BizException("商品不存在");
            }
            for (ProductSku oldSku : skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getSpuId, spu.getId()))) {
                oldSkuMap.put(oldSku.getId(), oldSku);
            }
            spuMapper.updateById(spu);
            // 修改时全量重建 SKU（学习项目简化：删除旧 SKU 后重新插入）
            skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, spu.getId()));
        }
        if (dto.getSkuList() != null) {
            for (ProductSku sku : dto.getSkuList()) {
                ProductSku old = sku.getId() == null ? null : oldSkuMap.get(sku.getId());
                sku.setId(null);
                sku.setSpuId(spu.getId());
                // spec 为 JSON 列：普通文本包装为 JSON 字符串后入库
                sku.setSpec(ProductJsonUtil.wrapText(sku.getSpec()));
                // 库存以原值为准（新增 SKU 默认为 0），销量/版本号重置
                sku.setStock(old == null ? 0 : old.getStock());
                sku.setSaleCount(old == null ? 0 : old.getSaleCount());
                sku.setVersion(0);
                if (sku.getStatus() == null) {
                    sku.setStatus((byte) 1);
                }
                skuMapper.insert(sku);
            }
        }
        evictDetailCache(spu.getId());
    }

    /** 上下架：上架前校验至少一个启用 SKU */
    public void updateStatus(Long spuId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("商品状态仅支持 0下架 / 1上架");
        }
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException("商品不存在");
        }
        if (status == 1) {
            long skuCount = skuMapper.selectCount(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getSpuId, spuId).eq(ProductSku::getStatus, 1));
            if (skuCount == 0) {
                throw new BizException("至少需要一个启用状态的 SKU 才能上架");
            }
        }
        spu.setStatus(status.byteValue());
        spuMapper.updateById(spu);
        evictDetailCache(spuId);
    }

    /** 删除（先下架再删；物理删除 SPU + SKU） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException("商品不存在");
        }
        if (spu.getStatus() == 1) {
            throw new BizException("请先下架商品再删除");
        }
        skuMapper.delete(new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, spuId));
        spuMapper.deleteById(spuId);
        evictDetailCache(spuId);
    }

    // ==================== 前台查询 + 缓存三防 ====================

    /** 前台商品分页（仅上架，支持分类/品牌/关键词） */
    public Page<ProductSpu> portalPage(Long categoryId, Long brandId, String keyword, long page, long size) {
        return spuMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductSpu>()
                        .eq(ProductSpu::getStatus, 1)
                        .eq(categoryId != null, ProductSpu::getCategoryId, categoryId)
                        .eq(brandId != null, ProductSpu::getBrandId, brandId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(ProductSpu::getName, keyword)
                                .or().like(ProductSpu::getSubtitle, keyword))
                        .orderByDesc(ProductSpu::getSort, ProductSpu::getId));
    }

    /** 商品详情：多级缓存（13.3 L1 Caffeine → L2 Redis）+ 缓存三防（穿透=空值短缓存 / 击穿=互斥锁 / 雪崩=TTL 随机偏移） */
    public Map<String, Object> detail(Long spuId) {
        String key = DETAIL_KEY + spuId;
        try {
            // 0. L1 本地缓存（Caffeine）命中直接返回，热点详情不再重复走 Redis
            Map<String, Object> local = localCache.getIfPresent(spuId);
            if (local != null) {
                return local;
            }
            // 1. L2 Redis 缓存
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (NULL_MARK.equals(cached)) {
                    throw new BizException("商品不存在");
                }
                Map<String, Object> hit = objectMapper.readValue(cached, Map.class);
                // 回填 L1（下个请求 L1 直出）
                localCache.put(spuId, hit);
                return hit;
            }
            // 2. 缓存未命中：互斥锁防击穿（SETNX，锁持有 10s；拿不到锁短暂等待后重查缓存）
            String lockKey = LOCK_KEY + spuId;
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 双检：可能其他线程已回填
                    cached = stringRedisTemplate.opsForValue().get(key);
                    if (cached != null) {
                        if (NULL_MARK.equals(cached)) {
                            throw new BizException("商品不存在");
                        }
                        Map<String, Object> hit = objectMapper.readValue(cached, Map.class);
                        localCache.put(spuId, hit);
                        return hit;
                    }
                    Map<String, Object> detail = buildDetail(spuId);
                    if (detail == null) {
                        // 穿透：空值短缓存（5 分钟），防恶意查询直接打库
                        stringRedisTemplate.opsForValue().set(key, NULL_MARK, Duration.ofMinutes(5));
                        throw new BizException("商品不存在");
                    }
                    // 雪崩：TTL 随机偏移（基础 TTL + 0~jitter 随机秒）
                    long ttl = cacheTtl + ThreadLocalRandom.current().nextLong(0, cacheTtlJitter + 1);
                    stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(detail), Duration.ofSeconds(ttl));
                    // 回填 L1 本地缓存
                    localCache.put(spuId, detail);
                    return detail;
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            }
            // 3. 未抢到锁：短暂等待后重查缓存（最多 3s）
            long waitUntil = System.currentTimeMillis() + LOCK_WAIT_MS;
            while (System.currentTimeMillis() < waitUntil) {
                Thread.sleep(50);
                cached = stringRedisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (NULL_MARK.equals(cached)) {
                        throw new BizException("商品不存在");
                    }
                    Map<String, Object> hit = objectMapper.readValue(cached, Map.class);
                    localCache.put(spuId, hit);
                    return hit;
                }
            }
            // 超时兜底：直查库（学习项目可接受，生产建议改异步重建）
            Map<String, Object> detail = buildDetail(spuId);
            if (detail == null) {
                throw new BizException("商品不存在");
            }
            return detail;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("商品详情缓存读取异常，spuId={}，降级直查库", spuId, e);
            Map<String, Object> detail = buildDetail(spuId);
            if (detail == null) {
                throw new BizException("商品不存在");
            }
            return detail;
        }
    }

    /** 热销 Top N（仅上架，按销量倒序；预热数据源） */
    public List<ProductSpu> hotList(int limit) {
        return spuMapper.selectList(new LambdaQueryWrapper<ProductSpu>()
                .eq(ProductSpu::getStatus, 1)
                .orderByDesc(ProductSpu::getSales)
                .last("limit " + Math.max(1, limit)));
    }

    // ==================== 缓存预热（2.5） ====================
    // xxl-job 已接入：productPreload 为执行器任务，@Scheduled 保留作本地双通道兜底（幂等重复执行无害）

    @XxlJob("productPreload")
    @Scheduled(cron = "0 0 0/1 * * ?")
    public void scheduledPreload() {
        preload();
    }

    /** 预热：热销 Top N 详情写缓存（TTL 与正常详情一致，带随机偏移） */
    public int preload() {
        int count = 0;
        for (ProductSpu spu : hotList(preloadTop)) {
            try {
                Map<String, Object> detail = buildDetail(spu.getId());
                if (detail != null) {
                    long ttl = cacheTtl + ThreadLocalRandom.current().nextLong(0, cacheTtlJitter + 1);
                    stringRedisTemplate.opsForValue().set(DETAIL_KEY + spu.getId(),
                            objectMapper.writeValueAsString(detail), Duration.ofSeconds(ttl));
                    // 预热同时回填 L1 本地缓存
                    localCache.put(spu.getId(), detail);
                    count++;
                }
            } catch (Exception e) {
                log.warn("预热失败 spuId={}", spu.getId(), e);
            }
        }
        log.info("商品详情预热完成，共 {} 个", count);
        return count;
    }

    // ==================== 内部方法 ====================

    /** 组装详情（SPU + 启用 SKU 列表 + 分类/品牌名）；SPU 不存在或已下架返回 null */
    public Map<String, Object> buildDetail(Long spuId) {
        ProductSpu spu = spuMapper.selectById(spuId);
        if (spu == null || spu.getStatus() == 0) {
            return null;
        }
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSpuId, spuId)
                .eq(ProductSku::getStatus, 1)
                .orderByAsc(ProductSku::getId));
        // 前台展示：JSON 列解包为普通文本（spec=文本、pics=逗号分隔 URL）
        spu.setPics(ProductJsonUtil.unwrapPics(spu.getPics()));
        skus.forEach(sku -> sku.setSpec(ProductJsonUtil.unwrapText(sku.getSpec())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("spu", spu);
        data.put("skuList", skus);
        ProductCategory category = spu.getCategoryId() == null ? null : categoryMapper.selectById(spu.getCategoryId());
        ProductBrand brand = spu.getBrandId() == null ? null : brandMapper.selectById(spu.getBrandId());
        data.put("categoryName", category == null ? null : category.getName());
        data.put("brandName", brand == null ? null : brand.getName());
        return data;
    }

    /** 删除详情缓存（商品增删改/上下架时调用，保证缓存一致性：先更 DB 再双清 L1 本地 + L2 Redis） */
    public void evictDetailCache(Long spuId) {
        localCache.invalidate(spuId);
        stringRedisTemplate.delete(DETAIL_KEY + spuId);
    }
}
