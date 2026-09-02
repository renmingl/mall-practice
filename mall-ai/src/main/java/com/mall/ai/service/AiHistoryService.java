package com.mall.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.ai.model.AiUser;
import com.mall.ai.model.ChatMessage;
import com.mall.ai.model.ChatSession;
import com.mall.common.exception.BizException;
import com.mall.mbg.entity.AiChatMessage;
import com.mall.mbg.mapper.AiChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话历史服务（阶段 9 16.3）：登录态会话入库 ai_chat_message、会话列表、历史消息加载
 * 游客不落库（无 userId 无历史，普通问答无状态）；同一会话多轮共享 sessionId，后端按会话拼上下文
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHistoryService {

    private final AiChatMessageMapper messageMapper;

    /** 落库一条消息（user / assistant）；游客不入库；DB 异常只告警不阻断对话 */
    public void save(AiUser user, String scene, String sessionId, String role, String content) {
        if (user == null || !user.isLoggedIn() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            AiChatMessage message = new AiChatMessage();
            message.setScene(scene);
            message.setSessionId(sessionId);
            message.setUserId(user.userId());
            message.setUserType(user.userType());
            message.setRole(role);
            message.setContent(content);
            message.setCreateTime(LocalDateTime.now());
            messageMapper.insert(message);
        } catch (Exception e) {
            // 历史入库失败不应阻断本轮问答（降级为无状态会话）
            log.warn("AI history save failed: scene={}, role={}, err={}", scene, role, e.getMessage());
        }
    }

    /**
     * 会话列表（按 scene + 用户，最近会话在前）：摘要 = 该会话最后一条 user 消息前 24 字 + 消息总数
     */
    public List<ChatSession> listSessions(AiUser user, String scene, int limit) {
        if (user == null || !user.isLoggedIn()) {
            return List.of();
        }
        List<AiChatMessage> latestUser = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getScene, scene)
                .eq(AiChatMessage::getUserId, user.userId())
                .eq(AiChatMessage::getRole, "user")
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit));
        // 同会话多轮只取最新一轮（按 sessionId 去重保序）
        Map<String, AiChatMessage> perSession = latestUser.stream()
                .collect(Collectors.toMap(AiChatMessage::getSessionId, m -> m, (a, b) -> a));
        return perSession.entrySet().stream()
                .sorted((a, b) -> b.getValue().getCreateTime().compareTo(a.getValue().getCreateTime()))
                .limit(limit)
                .map(e -> {
                    String sessionId = e.getKey();
                    AiChatMessage last = e.getValue();
                    Long total = messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                            .eq(AiChatMessage::getScene, scene)
                            .eq(AiChatMessage::getSessionId, sessionId));
                    String preview = last.getContent();
                    if (preview != null && preview.length() > 24) {
                        preview = preview.substring(0, 24) + "…";
                    }
                    return new ChatSession(sessionId, preview, total, last.getCreateTime());
                })
                .toList();
    }

    /**
     * 历史消息（按时间正序，供前端展示与后端拼上下文）
     */
    public List<ChatMessage> listMessages(AiUser user, String scene, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        // 归属校验：登录用户只能查自己的会话（游客不落库，无此路径）
        if (user == null || !user.isLoggedIn()) {
            throw new BizException("游客无历史会话");
        }
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getScene, scene)
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getUserId, user.userId()));
        if (count == null || count == 0) {
            throw new BizException("会话不存在或无权访问");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getScene, scene)
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getId))
                .stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();
    }

    /**
     * 会话上下文（最近 N 轮，不含本次问题）：后端拼 OpenAI messages 用
     * 注意：调用方已把当前问题落库（save 先于本方法），取回的历史尾部即刚保存的当前问题，
     * 必须剔除（连同历史中可能的悬空提问），否则当前问题会在 messages 中重复出现
     */
    public List<ChatMessage> loadContext(AiUser user, String scene, String sessionId, int maxTurns) {
        if (user == null || !user.isLoggedIn() || sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        List<AiChatMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getScene, scene)
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getUserId, user.userId())
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + (maxTurns * 2)));
        List<ChatMessage> messages = new java.util.ArrayList<>(rows.stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList());
        // 剔除尾部 user 消息（刚落库的当前问题；若上一轮回答中断，历史末尾的悬空提问一并剔除），
        // 保证上下文以最近一轮 assistant 回答结尾，避免连续 user 消息干扰模型
        while (!messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).role())) {
            messages = messages.subList(0, messages.size() - 1);
        }
        return messages;
    }
}
