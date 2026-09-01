package com.mall.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.mall.search.document.ProductDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品索引管理（阶段 8 13.2）：索引幂等创建、reindex 全量重建、单文档 upsert/删除（Canal 增量调用）
 * 索引映射说明：ES 官方镜像未装 ik 分词插件，text 字段用 standard 分析器（中文按字切分）；
 * 生产可换 ik_max_word 并重建索引，检索/高亮代码无需改动
 * @author renmingl
 * @date 2026-09-01 15:50:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    public static final String INDEX = "mall_product";

    /** 索引映射（name/subtitle/categoryName/brandName 参与全文检索与高亮；keyword 子字段供排序/精确过滤） */
    private static final String MAPPING_JSON = """
            {
              "mappings": {
                "properties": {
                  "spuId": {"type": "long"},
                  "spuCode": {"type": "keyword"},
                  "name": {"type": "text", "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}},
                  "subtitle": {"type": "text"},
                  "categoryId": {"type": "long"},
                  "categoryName": {"type": "text", "fields": {"keyword": {"type": "keyword", "ignore_above": 64}}},
                  "brandId": {"type": "long"},
                  "brandName": {"type": "text", "fields": {"keyword": {"type": "keyword", "ignore_above": 64}}},
                  "pic": {"type": "keyword", "index": false},
                  "price": {"type": "double"},
                  "sales": {"type": "long"},
                  "status": {"type": "byte"},
                  "createTime": {"type": "date", "format": "yyyy-MM-dd HH:mm:ss"}
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 幂等建索引：不存在则按映射创建（reindex 前调用） */
    public void ensureIndex() {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
            if (!exists) {
                client.indices().create(c -> c.index(INDEX)
                        .mappings(m -> m.withJson(new StringReader(MAPPING_JSON))));
                log.info("ES 索引 {} 创建完成", INDEX);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ES 索引初始化失败: " + e.getMessage(), e);
        }
    }

    /** 索引信息（存在性 + 文档数） */
    public Map<String, Object> indexInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("index", INDEX);
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
            info.put("exists", exists);
            if (exists) {
                var resp = client.count(c -> c.index(INDEX));
                info.put("docCount", resp.count());
            }
        } catch (ElasticsearchException | java.io.IOException e) {
            info.put("error", e.getMessage());
        }
        return info;
    }

    /** reindex 全量重建：DB（product_spu + 最低 SKU 价 + 分类/品牌名）→ bulk 写入 ES */
    public int reindex() {
        ensureIndex();
        List<ProductDoc> docs = jdbcTemplate.query(SPU_SELECT_SQL + " ORDER BY s.id", (rs, rowNum) -> mapDoc(rs));
        if (docs.isEmpty()) {
            return 0;
        }
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (ProductDoc doc : docs) {
            br.operations(op -> op.index(io -> io.index(INDEX)
                    .id(String.valueOf(doc.getSpuId()))
                    .document(doc)));
        }
        try {
            BulkResponse response = client.bulk(br.build());
            if (response.errors()) {
                throw new IllegalStateException("reindex 部分文档写入失败，请查看 ES 日志");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("reindex 写入 ES 失败: " + e.getMessage(), e);
        }
        log.info("商品索引全量重建完成，共 {} 条", docs.size());
        return docs.size();
    }

    /** 单文档 upsert（Canal binlog 增量同步调用：product_spu 变更） */
    public void upsert(ProductDoc doc) {
        try {
            client.index(io -> io.index(INDEX)
                    .id(String.valueOf(doc.getSpuId()))
                    .document(doc)
                    .refresh(Refresh.True));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("写入 ES 失败 spuId=" + doc.getSpuId(), e);
        }
    }

    /** 删除文档（Canal 增量同步调用：商品删除/下架） */
    public void deleteById(Long spuId) {
        try {
            client.delete(d -> d.index(INDEX).id(String.valueOf(spuId)));
        } catch (ElasticsearchException e) {
            // 404 视为已删除，忽略
            if (e.status() != 404) {
                throw e;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("删除 ES 文档失败 spuId=" + spuId, e);
        }
    }

    /** 按 spuId 重查 DB 组装文档（Canal 增量同步复用；商品不存在/已下架返回 null） */
    public ProductDoc loadDoc(Long spuId) {
        List<ProductDoc> docs = jdbcTemplate.query(SPU_SELECT_SQL + " AND s.id = ?",
                ps -> ps.setLong(1, spuId), (rs, rowNum) -> mapDoc(rs));
        return docs.isEmpty() ? null : docs.get(0);
    }

    /** 商品文档查询 SQL（status=1 上架；loadDoc 追加主键条件） */
    private static final String SPU_SELECT_SQL = """
            SELECT s.id AS spu_id, s.spu_code, s.name, s.subtitle, s.category_id, s.brand_id,
                   s.pic, s.sales, s.status, s.create_time,
                   c.name AS category_name, b.name AS brand_name,
                   (SELECT MIN(k.price) FROM product_sku k
                     WHERE k.spu_id = s.id AND k.status = 1) AS price
            FROM product_spu s
            LEFT JOIN product_category c ON c.id = s.category_id
            LEFT JOIN product_brand b ON b.id = s.brand_id
            WHERE s.status = 1
            """;

    /** 行 → 文档映射（reindex 全量 / loadDoc 单条共用） */
    private ProductDoc mapDoc(java.sql.ResultSet rs) throws java.sql.SQLException {
        ProductDoc doc = new ProductDoc();
        doc.setSpuId(rs.getLong("spu_id"));
        doc.setSpuCode(rs.getString("spu_code"));
        doc.setName(rs.getString("name"));
        doc.setSubtitle(rs.getString("subtitle"));
        long categoryId = rs.getLong("category_id");
        doc.setCategoryId(rs.wasNull() ? null : categoryId);
        long brandId = rs.getLong("brand_id");
        doc.setBrandId(rs.wasNull() ? null : brandId);
        doc.setCategoryName(rs.getString("category_name"));
        doc.setBrandName(rs.getString("brand_name"));
        doc.setPic(rs.getString("pic"));
        doc.setPrice(rs.getBigDecimal("price"));
        doc.setSales(rs.getInt("sales"));
        doc.setStatus(rs.getByte("status"));
        Timestamp createTime = rs.getTimestamp("create_time");
        doc.setCreateTime(createTime == null ? null : TIME_FMT.format(createTime.toLocalDateTime()));
        return doc;
    }
}
