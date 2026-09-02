import request from './request'

// ---------- AI 客服（网关 /api/ai → mall-ai 9200，scene=portal） ----------
// SSE 流式走原生 fetch（axios 拦截器按 Result JSON 解包，无法处理事件流）；
// 登录态由 Authorization 头携带（网关校验通过后注入 X-User-Id/X-User-Type，后端据此能力分层）

export interface AiModelInfo {
  provider: string
  label: string
  model: string
  available: boolean
}

export interface AiSession {
  sessionId: string
  preview: string
  total: number
  createTime: string
}

export interface AiHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiChatBody {
  provider?: string
  scene: 'portal'
  sessionId?: string
  message: string
}

/** 模型清单（未配置 Key 的模型 available=false） */
export function getAiConfig() {
  return request.get<AiModelInfo[]>('/ai/config')
}

/** 会话列表（仅登录会员返回本人数据） */
export function getAiSessions() {
  return request.get<AiSession[]>('/ai/sessions', { params: { scene: 'portal' } })
}

/** 会话历史消息（正序；仅本人会话） */
export function getAiMessages(sessionId: string) {
  return request.get<AiHistoryMessage[]>('/ai/messages', { params: { scene: 'portal', sessionId } })
}

/**
 * SSE 流式对话：逐块回调 onDelta，返回完整回复
 * 帧协议与 mall-ai 对齐：data:{"delta":"..."} / data:{"done":true} / data:{"error":"..."}
 */
export async function streamAiChat(body: AiChatBody, onDelta: (delta: string) => void): Promise<string> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  const resp = await fetch(`${base}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('portal_access_token') || ''}`
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
