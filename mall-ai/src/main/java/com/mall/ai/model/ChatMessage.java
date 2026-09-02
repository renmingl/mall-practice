package com.mall.ai.model;

/**
 * 对话消息（OpenAI 协议角色：system / user / assistant）
 * @author renmingl
 * @date 2026-09-02 13:42:22
 */
public record ChatMessage(String role, String content) {
}
