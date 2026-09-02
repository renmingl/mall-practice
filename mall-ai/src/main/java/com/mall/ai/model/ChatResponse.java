package com.mall.ai.model;

/**
 * 对话响应体（非流式 JSON）：reply 为模型助手回复；sessionId 为本次会话 ID（登录态落库后供历史续聊），
 * 游客每次对话不落库，sessionId 仍返回以便前端本地上送时保持一致
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
public record ChatResponse(String provider, String label, String model, String sessionId, String reply) {
}
