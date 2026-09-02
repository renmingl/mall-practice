import request from './request'

// ---------- AI 助手（网关 /api/ai → mall-ai 9200） ----------
// SSE 流式走原生 fetch（不经 axios 拦截器：拦截器会按 Result JSON 解包，无法处理事件流）

/** 模型清单项（available=false 表示该模型未配置 API Key，选择器置灰） */
export interface AiModelInfo {
  provider: string
  label: string
  model: string
  available: boolean
}

/** 非流式对话响应 */
export interface AiChatResponse {
  provider: string
  providerLabel: string
  model: string
  sessionId?: string
  reply: string
}

/** 会话摘要（历史列表用） */
export interface AiSession {
  sessionId: string
  preview: string
  total: number
  createTime: string
}

/** 会话内单条消息（历史详情用） */
export interface AiHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

/** 对话请求体 */
export interface AiChatBody {
  provider?: string
  scene: 'admin' | 'portal'
  sessionId?: string
  message: string
}

/** 模型清单 */
export function getAiConfig() {
  return request.get<AiModelInfo[]>('/ai/config')
}

/** 发起对话（非流式 JSON，备用；主链路走 streamAiChat） */
export function postAiChat(body: AiChatBody) {
  return request.post<AiChatResponse>('/ai/chat', body)
}

/** 会话列表（当前登录管理员；游客调用返回空） */
export function getAiSessions(scene: 'admin' | 'portal') {
  return request.get<AiSession[]>('/ai/sessions', { params: { scene } })
}

/** 会话历史消息（正序；仅本人会话，越权返回错误） */
export function getAiMessages(scene: 'admin' | 'portal', sessionId: string) {
  return request.get<AiHistoryMessage[]>('/ai/messages', { params: { scene, sessionId } })
}

/**
 * SSE 流式对话：逐块回调 onDelta，返回完整回复文本
 * 帧协议与 mall-ai 对齐：data:{"delta":"..."} 增量 / data:{"done":true} 结束 / data:{"error":"..."} 异常
 * 非 SSE 响应（网关 401/错误 JSON）兼容读取 message 抛出
 */
export async function streamAiChat(body: AiChatBody, onDelta: (delta: string) => void): Promise<string> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const resp = await fetch(`${base}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('admin_access_token') || ''}`
    },
    body: JSON.stringify(body)
  })
  if (!resp.ok || !resp.body) {
    const text = await resp.text()
    let message = `AI 连接失败（HTTP ${resp.status}）`
    try {
      message = (JSON.parse(text) as { message?: string }).message || message
    } catch {
      // 非 JSON 响应保持默认提示
    }
    throw new Error(message)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let reply = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx: number
    // SSE 帧按行分割：data:{...}
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim()
      buffer = buffer.slice(idx + 1)
      if (!line.startsWith('data:')) continue
      const payload = line.slice(5).trim()
      if (!payload || payload === '[DONE]') continue
      let frame: { delta?: string; done?: boolean; error?: string }
      try {
        frame = JSON.parse(payload)
      } catch {
        continue
      }
      if (frame.error) {
        throw new Error(frame.error)
      }
      if (frame.delta) {
        reply += frame.delta
        onDelta(frame.delta)
      }
      if (frame.done) {
        return reply
      }
    }
  }
  return reply
}
