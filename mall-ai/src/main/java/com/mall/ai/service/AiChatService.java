package com.mall.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.ai.config.AiProperties;
import com.mall.ai.model.AiUser;
import com.mall.ai.model.ChatMessage;
import com.mall.ai.model.ChatResponse;
import com.mall.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * AI 对话核心服务（阶段 9 16.1/16.3）：OpenAI 兼容协议直连各模型供应商
 * Key 由使用者自配（docker/.env 环境变量注入），未配置 key 的模型视为不可用并明确报错；
 * 多轮上下文 = 系统提示（能力分层 + 知识检索 + 数据供给）+ 会话历史（ai_chat_message，最近 6 轮）+ 当前问题；
 * 登录态消息落库，游客无状态问答
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /** 会话上下文最大轮数（含当前问题共 6 轮，超长会话自动截旧） */
    private static final int MAX_HISTORY_TURNS = 6;

    private final AiProperties aiProperties;
    private final AiDataService aiDataService;
    private final AiHistoryService aiHistoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 可用模型清单（供 GET /api/ai/config）：available = 已配置该模型且 api-key 非空
     * 前端据此渲染选择器，未配置的模型置灰并提示
     */
    public List<Map<String, Object>> availableModels() {
        List<Map<String, Object>> list = new ArrayList<>();
        aiProperties.getProviders().forEach((id, p) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider", id);
            item.put("label", p.getLabel());
            item.put("model", p.getModel());
            item.put("available", isAvailable(p));
            list.add(item);
        });
        return list;
    }

    /**
     * 发起一轮对话（非流式）：provider 为空时自动选第一个可用模型；登录态自动落库 user + assistant
     */
    public ChatResponse chat(String providerId, AiUser user, String scene, String sessionId, String message) {
        validateMessage(message);
        AiProperties.Provider provider = resolveProvider(providerId);
        String sid = normalizeSessionId(sessionId);
        // 先落库用户问题（登录态），失败不阻断对话
        aiHistoryService.save(user, scene, sid, "user", message);
        List<ChatMessage> messages = buildMessages(user, scene, sid, message);
        String reply = invoke(provider, messages);
        aiHistoryService.save(user, scene, sid, "assistant", reply);
        return new ChatResponse(providerIdOf(provider), provider.getLabel(), provider.getModel(), sid, reply);
    }

    /**
     * 发起一轮对话（SSE 流式）：delta 逐块回调（调用方推给前端），返回完整回复（流结束后落库）
     * 上游中途异常抛 BizException：此时已推送的部分内容不回滚，assistant 不落库
     */
    public String streamChat(String providerId, AiUser user, String scene, String sessionId, String message,
                             Consumer<String> onDelta) {
        validateMessage(message);
        AiProperties.Provider provider = resolveProvider(providerId);
        String sid = normalizeSessionId(sessionId);
        aiHistoryService.save(user, scene, sid, "user", message);
        List<ChatMessage> messages = buildMessages(user, scene, sid, message);
        StringBuilder reply = new StringBuilder();
        invokeStream(provider, messages, delta -> {
            reply.append(delta);
            if (onDelta != null) {
                onDelta.accept(delta);
            }
        });
        String full = reply.toString().trim();
        aiHistoryService.save(user, scene, sid, "assistant", full);
        return full;
    }

    /** 校验请求消息非空 */
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BizException("问题不能为空");
        }
        if (message.length() > 2000) {
            throw new BizException("问题过长（上限 2000 字）");
        }
    }

    /** sessionId 缺省生成（UUID 短码）；游客的 sessionId 仅用于请求内一致，不落库；长度上限与库表字段对齐 */
    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        if (sessionId.length() > 64) {
            throw new BizException("sessionId 不合法（上限 64 字符）");
        }
        return sessionId;
    }

    /** 组装 OpenAI messages：system（能力/知识/数据）+ 历史（登录态按会话取，游客为空）+ 当前问题 */
    private List<ChatMessage> buildMessages(AiUser user, String scene, String sessionId, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", aiDataService.buildSystemPrompt(user, scene, question)));
        messages.addAll(aiHistoryService.loadContext(user, scene, sessionId, MAX_HISTORY_TURNS));
        messages.add(new ChatMessage("user", question));
        return messages;
    }

    /** 解析目标模型：providerId 为空取第一个可用；指定但不存在/不可用则明确报错 */
    private AiProperties.Provider resolveProvider(String providerId) {
        Map<String, AiProperties.Provider> providers = aiProperties.getProviders();
        if (providerId == null || providerId.isBlank()) {
            return providers.values().stream()
                    .filter(this::isAvailable)
                    .findFirst()
                    .orElseThrow(() -> new BizException("尚未配置任何模型的 API Key，请在 docker/.env 中配置后重启（见 README「AI 能力」章节）"));
        }
        AiProperties.Provider provider = providers.get(providerId);
        if (provider == null) {
            throw new BizException("未知模型：" + providerId + "，可用模型见 GET /api/ai/config");
        }
        if (!isAvailable(provider)) {
            throw new BizException("模型【" + provider.getLabel() + "】未配置 API Key，暂不可用，请在 docker/.env 中配置后重启（见 README「AI 能力」章节）");
        }
        return provider;
    }

    private String providerIdOf(AiProperties.Provider provider) {
        return aiProperties.getProviders().entrySet().stream()
                .filter(e -> e.getValue() == provider)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("unknown");
    }

    /** 可用性 = 已配置 base-url 与 api-key（key 为空视为未配置） */
    private boolean isAvailable(AiProperties.Provider p) {
        return p.getApiKey() != null && !p.getApiKey().isBlank();
    }

    /** 日志截断：上游错误响应可能很长，只留前 500 字符，避免日志膨胀 */
    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...(已截断)";
    }

    /** 调用 OpenAI 兼容协议 POST {baseUrl}/chat/completions（非流式），返回模型回复文本 */
    private String invoke(AiProperties.Provider provider, List<ChatMessage> messages) {
        try {
            String body = buildRequestBody(provider.getModel(), messages, false);
            HttpRequest request = buildRequest(provider, body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            if (response.statusCode() != 200) {
                log.warn("AI upstream error: provider={}, status={}, body={}", provider.getLabel(), response.statusCode(), truncate(respBody));
                throw new BizException("模型【" + provider.getLabel() + "】调用失败（HTTP " + response.statusCode() + "），请检查 API Key 是否有效");
            }
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode choice = root.path("choices").path(0);
            if (choice.isMissingNode()) {
                log.warn("AI upstream empty choices: body={}", truncate(respBody));
                throw new BizException("模型【" + provider.getLabel() + "】返回异常：无有效回复内容");
            }
            return choice.path("message").path("content").asText("").trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI upstream call failed: provider={}", provider.getLabel(), e);
            throw new BizException("模型【" + provider.getLabel() + "】连接失败：" + e.getMessage());
        }
    }

    /**
     * 调用 OpenAI 兼容协议（流式 stream=true）：逐行解析 SSE 的 data 帧，
     * 每个非空 delta 回调一次；结束帧 data: [DONE] 停止
     */
    private void invokeStream(AiProperties.Provider provider, List<ChatMessage> messages, Consumer<String> deltaSink) {
        try {
            String body = buildRequestBody(provider.getModel(), messages, true);
            HttpRequest request = buildRequest(provider, body);
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            // 统一用 try-with-resources 持有响应流：非 200 与正常消费结束后都会关闭底层连接
            try (java.util.stream.Stream<String> lines = response.body()) {
                if (response.statusCode() != 200) {
                    String errBody = lines.limit(500).reduce("", String::concat);
                    log.warn("AI upstream error: provider={}, status={}, body={}", provider.getLabel(), response.statusCode(), truncate(errBody));
                    throw new BizException("模型【" + provider.getLabel() + "】调用失败（HTTP " + response.statusCode() + "），请检查 API Key 是否有效");
                }
                lines.forEach(line -> {
                    String text = line.trim();
                    if (!text.startsWith("data:")) {
                        return;
                    }
                    String payload = text.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        return;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(payload);
                        String delta = root.path("choices").path(0).path("delta").path("content").asText("");
                        if (!delta.isEmpty()) {
                            deltaSink.accept(delta);
                        }
                    } catch (Exception parseError) {
                        log.warn("AI upstream stream parse skip: {}", parseError.getMessage());
                    }
                });
            }
        } catch (IllegalStateException e) {
            // 推送方主动终止（SSE 客户端断开），非上游故障：原样上抛不再包装
            throw e;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI upstream stream call failed: provider={}", provider.getLabel(), e);
            throw new BizException("模型【" + provider.getLabel() + "】流式连接失败：" + e.getMessage());
        }
    }

    private HttpRequest buildRequest(AiProperties.Provider provider, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(provider.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + provider.getApiKey())
                .timeout(aiProperties.getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String buildRequestBody(String model, List<ChatMessage> messages, boolean stream) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> msgs = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", m.role());
            item.put("content", m.content());
            msgs.add(item);
        }
        body.put("messages", msgs);
        body.put("stream", stream);
        return objectMapper.writeValueAsString(body);
    }
}
