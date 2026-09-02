package com.mall.ai.model;

import java.time.LocalDateTime;

/**
 * 会话摘要（阶段 9 16.3 历史会话列表项）：由 ai_chat_message 聚合
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
public record ChatSession(String sessionId, String preview, long total, LocalDateTime createTime) {
}
