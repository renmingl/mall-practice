package com.mall.ai.model;

/**
 * 对话请求体（阶段 9 16.3）：provider 为空时服务端自动选第一个可用模型；
 * scene 标识会话场景（admin 后台助手 / portal 前台客服，登录态时用于历史隔离与能力分层校验）；
 * message 为当前问题，多轮上下文由服务端按 sessionId 从历史表加载，不再全量随请求传递
 * @author renmingl
 * @date 2026-09-02 15:13:13
 */
public record ChatRequest(String provider, String scene, String sessionId, String message) {
}
