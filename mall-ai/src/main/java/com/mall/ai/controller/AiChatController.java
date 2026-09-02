package com.mall.ai.controller;

import com.mall.ai.model.AiUser;
import com.mall.ai.model.ChatRequest;
import com.mall.ai.model.ChatResponse;
import com.mall.ai.model.ChatSession;
import com.mall.ai.service.AiChatService;
import com.mall.ai.service.AiHistoryService;
import com.mall.common.exception.BizException;
import com.mall.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 助手接口（阶段 9 16.1/16.3）：模型配置 / 问答（流式与非流式）/ 会话历史
 * 路由 /api/ai/**（网关转发 mall-ai）：游客免登录普通问答；带 token 的请求网关透传
 * X-User-Id / X-User-Type 后在此解析出 AiUser，实现登录态能力分层与历史隔离
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService chatService;
    private final AiHistoryService historyService;

    /** 模型清单：前端据此渲染选择器；available=false 表示该模型未配置 API Key，置灰不可选 */
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> config() {
        return Result.success(chatService.availableModels());
    }

    /** 发起对话（非流式 JSON）：provider 为空时服务端自动选第一个可用模型 */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @RequestHeader(value = "X-User-Type", required = false) String userType) {
        AiUser user = resolveUser(userId, userType);
        String scene = resolveScene(request.scene(), user);
        ChatResponse response = chatService.chat(request.provider(), user, scene, request.sessionId(), request.message());
        return Result.success(response);
    }

    /**
     * 发起对话（SSE 流式）：data: {"delta":"..."} 逐块推送；结束 data: {"done":true} 后关闭；
     * 中途异常 data: {"error":"..."} 后关闭（含参数/权限校验失败，协议统一走 error 帧）；
     * 内容类型非 SSE 的响应（异常 JSON）由调用方兼容处理
     * 注意：模型调用与逐帧推送在工作线程执行——SseEmitter 在 controller 返回前 send 的事件会被
     * Spring 缓冲到方法返回后才统一写出（流式退化为一次性到达），故这里只建 emitter 立即返回
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-User-Type", required = false) String userType) {
        SseEmitter emitter = new SseEmitter(0L);
        // 工作线程不继承 MDC，手动透传 traceId（commonPool 线程无上下文，clear 安全）
        Map<String, String> trace = MDC.getCopyOfContextMap();
        CompletableFuture.runAsync(() -> {
            if (trace != null && !trace.isEmpty()) {
                MDC.setContextMap(trace);
            }
            try {
                AiUser user = resolveUser(userId, userType);
                String scene = resolveScene(request.scene(), user);
                chatService.streamChat(request.provider(), user, scene, request.sessionId(), request.message(),
                        delta -> send(emitter, Map.of("delta", delta)));
                send(emitter, Map.of("done", true));
                emitter.complete();
            } catch (Exception e) {
                // 客户端断开或上游异常：推送 error 帧后关闭（断开时 send 失败被忽略）
                String message = e instanceof BizException biz ? biz.getMessage() : "AI 服务异常：" + e.getMessage();
                sendQuietly(emitter, Map.of("error", message));
                emitter.complete();
            } finally {
                MDC.clear();
            }
        });
        return emitter;
    }

    /** 会话列表（登录态）：scene=admin|portal，按当前用户隔离 */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> sessions(@RequestParam("scene") String scene,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                              @RequestHeader(value = "X-User-Type", required = false) String userType) {
        AiUser user = resolveUser(userId, userType);
        resolveScene(scene, user);
        return Result.success(historyService.listSessions(user, scene, 20));
    }

    /** 会话历史消息（登录态）：按时间正序返回该会话全部消息，仅限本人会话 */
    @GetMapping("/messages")
    public Result<List<Map<String, Object>>> messages(@RequestParam("scene") String scene,
                                                      @RequestParam("sessionId") String sessionId,
                                                      @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                      @RequestHeader(value = "X-User-Type", required = false) String userType) {
        AiUser user = resolveUser(userId, userType);
        resolveScene(scene, user);
        return Result.success(historyService.listMessages(user, scene, sessionId).stream()
                .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
                .toList());
    }

    /** 解析用户上下文：网关透传头；无头 = 游客 */
    private AiUser resolveUser(Long userId, String userType) {
        if (userId == null) {
            return new AiUser(null, null);
        }
        return new AiUser(userId, "ADMIN".equals(userType) ? "ADMIN" : "MEMBER");
    }

    /** 场景校验：admin 场景仅管理员可用（防买家越权访问后台助手历史）；portal 场景游客/买家均可 */
    private String resolveScene(String scene, AiUser user) {
        if (!"admin".equals(scene) && !"portal".equals(scene)) {
            throw new BizException("scene 参数不合法（admin | portal）");
        }
        if ("admin".equals(scene) && (user == null || !user.isAdmin())) {
            throw new BizException("后台 AI 助手仅管理员可用");
        }
        return scene;
    }

    /** 推送一帧；客户端已断开时终止流并抛出，结束推送任务（异常路径由外层 catch 收敛） */
    private void send(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception e) {
            // 客户端断开（刷新/关闭页面）：终止流，停止后续推送
            emitter.completeWithError(e);
            throw new IllegalStateException("SSE 连接已断开", e);
        }
    }

    /** 静默推送一帧（收尾 error 帧用）：客户端已断开时忽略，不再抛异常 */
    private void sendQuietly(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception ignored) {
            // 客户端已断开，忽略
        }
    }
}
