package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.mall.search.document.ProductDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品搜索服务（阶段 8 13.2）：全文检索 + 高亮（multi_match 权重：名称 3 / 副标题 2 / 分类名 / 品牌名）；
 * 联想（match_phrase_prefix 名称前缀）；仅搜 status=1 上架商品
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ElasticsearchClient client;
    private final ProductIndexService indexService;

    /**
     * 分页搜索：keyword 非空走 multi_match 全文检索（带 <em> 高亮）；
     * categoryId 可选过滤；返回记录含高亮片段（highlightName/highlightSubtitle）
     */
    public Map<String, Object> search(String keyword, Long categoryId, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int capped = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        boolean keywordHit = StringUtils.hasText(keyword);
        List<Map<String, Object>> records = new ArrayList<>();
        long total = 0;
        try {
            SearchResponse<ProductDoc> resp = client.search(s -> {
                    var q = s.index(ProductIndexService.INDEX).query(qb -> qb.bool(b -> {
                        b.filter(f -> f.term(t -> t.field("status").value(1)));
                        if (categoryId != null) {
                            b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
                        }
                        if (keywordHit) {
                            b.must(m -> m.multiMatch(mm -> mm.query(keyword)
                                    .fields("name^3", "subtitle^2", "categoryName", "brandName")));
                        } else {
                            // 无关键字：按销量倒序（分类浏览兜底）
                            b.must(m -> m.matchAll(ma -> ma));
                        }
                        return b;
                    }));
                    if (keywordHit) {
                        q.highlight(h -> h.fields("name", f -> f.preTags("<em>").postTags("</em>"))
                                .fields("subtitle", f -> f.preTags("<em>").postTags("</em>")));
                    }
                    q.sort(so -> so.field(f -> f.field("_score").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                            .sort(so -> so.field(f -> f.field("sales").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                            .from(from).size(capped);
                    return q;
                }, ProductDoc.class);

                total = resp.hits().total() == null ? 0 : resp.hits().total().value();
                resp.hits().hits().forEach(hit -> {
                    ProductDoc doc = hit.source();
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (doc != null) {
                        row.put("spuId", doc.getSpuId());
                        row.put("name", doc.getName());
                        row.put("subtitle", doc.getSubtitle());
                        row.put("pic", doc.getPic());
                        row.put("price", doc.getPrice());
                        row.put("sales", doc.getSales());
                        row.put("categoryName", doc.getCategoryName());
                        row.put("brandName", doc.getBrandName());
                    }
                    if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                        List<String> names = hit.highlight().get("name");
                        if (names != null && !names.isEmpty()) {
                            row.put("highlightName", names.get(0));
                        }
                        List<String> subtitles = hit.highlight().get("subtitle");
                        if (subtitles != null && !subtitles.isEmpty()) {
                            row.put("highlightSubtitle", subtitles.get(0));
                        }
                    }
                    records.add(row);
                });
            } catch (Exception e) {
                // ES 不可用：降级空结果（页面提示搜索暂不可用）
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("total", 0L);
                fallback.put("records", records);
                fallback.put("fallback", true);
                fallback.put("error", e.getMessage());
                return fallback;
            }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("records", records);
        return result;
    }

    /** 搜索联想：名称 match_phrase_prefix 取前 8 条去重（中文按前缀匹配，英文/数字走标准分词） */
    public List<String> suggest(String prefix) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(prefix)) {
            return result;
        }
        try {
            SearchResponse<ProductDoc> resp = client.search(s -> s
                    .index(ProductIndexService.INDEX)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("status").value(1)))
                            .must(m -> m.matchPhrasePrefix(mp -> mp.field("name").query(prefix)))))
                    .size(8), ProductDoc.class);
            resp.hits().hits().forEach(hit -> {
                if (hit.source() != null && !result.contains(hit.source().getName())) {
                    result.add(hit.source().getName());
                }
            });
        } catch (Exception e) {
            // ES 不可用：返回空联想
        }
        return result;
    }
}
