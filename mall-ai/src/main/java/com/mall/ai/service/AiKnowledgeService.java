package com.mall.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目知识库检索（阶段 9 16.7）：语料 = 仓库 docs/ 专题文档（mall-ai pom 构建期复制进 classpath）
 * 单源维护：架构/业务调整只改 docs/ 一处，重新构建即随版本与镜像同步，无第二份人工维护的要点文件；
 * 检索 = 相邻双字 token 命中计数（轻量关键词匹配，不引 ES/向量库），命中度 Top-K 拼进对话上下文
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Slf4j
@Service
public class AiKnowledgeService {

    /** 拼入 system 的知识总长预算（docs 表格行/场景清单可达上千字符，防超长烧 token） */
    private static final int MAX_KNOWLEDGE_LENGTH = 4000;
    /** 单行超过该长度时按表格分隔符/中文标点切块（docs 原文粒度比原要点文件粗，需控块长） */
    private static final int MAX_BLOCK_LENGTH = 300;

    /** 语料块（docs 按行分块：行级粒度 = 检索精度与上下文噪音的平衡） */
    private final List<String> blocks = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            Resource[] docs = new PathMatchingResourcePatternResolver().getResources("classpath*:docs/*.md");
            for (Resource doc : docs) {
                load(doc);
            }
            log.info("AI knowledge loaded: {} docs, {} blocks", docs.length, blocks.size());
            if (blocks.isEmpty()) {
                log.warn("AI knowledge empty: docs not in classpath (source run via IDE please build with Maven first)");
            }
        } catch (Exception e) {
            log.error("AI knowledge load failed", e);
        }
    }

    /** 读取单篇 docs：跳过空行/标题/代码围栏与表格分隔线，正文行轻度清洗后分块入库 */
    private void load(Resource doc) throws Exception {
        boolean inCode = false;
        String pendingQa = null; // FAQ 问题行（等待紧随的 A 行合并，避免问答被行切拆散）
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(doc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // 跳过空行与标题行（标题无知识点；章节名随内容行命中已足够）
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                // 代码围栏与围栏内内容（命令行示例噪音大，不参与检索）
                if (trimmed.startsWith("```")) {
                    inCode = !inCode;
                    continue;
                }
                // 表格分隔线（|---|）与纯符号行
                if (inCode || trimmed.matches("^[\\s|\\-:]+$")) {
                    continue;
                }
                // FAQ 问答对：Q 行暂存，A 行紧随则合并为一块（faq.md 一问一答格式）
                if (pendingQa != null && !trimmed.startsWith("A：")) {
                    blocks.addAll(split(pendingQa));
                    pendingQa = null;
                }
                if (trimmed.startsWith("**Q：") || trimmed.startsWith("Q：")) {
                    pendingQa = cleanLine(trimmed);
                    continue;
                }
                if (pendingQa != null && trimmed.startsWith("A：")) {
                    blocks.addAll(split(pendingQa + "\n" + cleanLine(trimmed)));
                    pendingQa = null;
                    continue;
                }
                blocks.addAll(split(cleanLine(trimmed)));
            }
            if (pendingQa != null) {
                blocks.addAll(split(pendingQa));
            }
        }
    }

    /**
     * 检索 Top-K 命中块：question 的相邻双字 token 在块中出现即 +1（中文无空格，双字比单字噪音小）；
     * 命中度从高到低取，累计长度不超预算（防超长行把 system 撑爆）
     */
    public List<String> retrieve(String question, int topK) {
        List<String> tokens = tokenize(question);
        if (tokens.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> score = new LinkedHashMap<>();
        for (String block : blocks) {
            int hit = 0;
            for (String token : tokens) {
                if (block.contains(token)) {
                    hit++;
                }
            }
            if (hit > 0) {
                score.put(block, hit);
            }
        }
        List<String> result = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : score.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topK)
                .toList()) {
            if (total + e.getKey().length() > MAX_KNOWLEDGE_LENGTH) {
                break;
            }
            result.add(e.getKey());
            total += e.getKey().length();
        }
        return result;
    }

    /** 轻度清洗 markdown 噪音（粗体/行内代码/链接语法），保留可读文本供关键词命中与模型理解 */
    private String cleanLine(String line) {
        return line.replace("**", "").replace("`", "")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
    }

    /** 长行切块：表格分隔符与中文标点处优先切（docs 表格行/场景清单单行可达上千字符） */
    private List<String> split(String line) {
        if (line.length() <= MAX_BLOCK_LENGTH) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            cur.append(c);
            if (cur.length() >= MAX_BLOCK_LENGTH || (isSplitPoint(c) && cur.length() > 60)) {
                parts.add(cur.toString().trim());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString().trim());
        }
        return parts;
    }

    /** 表格单元格分隔符与中文句读都是天然切点 */
    private boolean isSplitPoint(char c) {
        return c == '|' || c == '。' || c == '；' || c == '！' || c == '？' || c == '，' || c == '、' || c == ' ';
    }

    /** 相邻双字 token（中文问题分词近似）；纯英文/数字场景退化为整词切分 */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String cleaned = text.replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9]", "");
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            String pair = cleaned.substring(i, i + 2);
            // 跳过跨语言的双字（如"案a"），保留纯中文双字与英文单词片段
            if (isPureChinese(pair) || isPureAscii(pair)) {
                tokens.add(pair);
            }
        }
        return tokens;
    }

    private boolean isPureChinese(String s) {
        return s.chars().allMatch(c -> c >= 0x4e00 && c <= 0x9fa5);
    }

    private boolean isPureAscii(String s) {
        return s.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
    }
}
