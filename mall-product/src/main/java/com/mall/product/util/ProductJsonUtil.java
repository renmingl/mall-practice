package com.mall.product.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品 JSON 列工具：product_sku.spec 与 product_spu.pics 均为 MySQL JSON 类型列。
 * 前后端以「普通文本」交互（spec=文本、pics=逗号分隔 URL），持久层统一做 JSON 包装/解包，
 * 避免非 JSON 文本直接写入 JSON 列触发 MySQL Invalid JSON 报错。
 * @author renmingl
 * @date 2026-08-27 11:00:00
 */
public final class ProductJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProductJsonUtil() {
    }

    /** 写入前包装：任意文本 → JSON 字符串（如 黑色 64G → "黑色 64G"）；null 原样返回 */
    public static String wrapText(String text) {
        if (text == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(text);
        } catch (Exception e) {
            return text;
        }
    }

    /** 读取后解包：JSON 字符串 → 文本（"黑色 64G" → 黑色 64G）；非 JSON 原样返回（容错历史脏数据） */
    public static String unwrapText(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, String.class);
        } catch (Exception e) {
            return json;
        }
    }

    /** 写入前包装：逗号分隔 URL → JSON 数组（a.jpg,b.jpg → ["a.jpg","b.jpg"]）；null/空串返回 null（JSON 列不接受空串） */
    public static String wrapPics(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<String> urls = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        try {
            return MAPPER.writeValueAsString(urls);
        } catch (Exception e) {
            return csv;
        }
    }

    /** 读取后解包：JSON 数组 → 逗号分隔 URL（["a.jpg","b.jpg"] → a.jpg,b.jpg）；非 JSON 原样返回 */
    public static String unwrapPics(String json) {
        if (json == null) {
            return null;
        }
        try {
            List<String> urls = MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            return String.join(",", urls);
        } catch (Exception e) {
            return json;
        }
    }
}
