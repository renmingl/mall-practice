<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAiConfig, getAiMessages, getAiSessions, streamAiChat,
  type AiModelInfo, type AiSession
} from '@/api/ai'

const scene = 'admin'

// ---------- 模型 / 会话状态 ----------
const models = ref<AiModelInfo[]>([])
const provider = ref<string>('') // 空 = 服务端自动选第一个可用模型
const sessions = ref<AiSession[]>([])
const sessionsLoading = ref(false)

/** 当前会话（null = 新会话）；发送时为空则本地生成并沿用，保证多轮历史归属同一会话 */
const activeSid = ref<string | null>(null)

interface UiMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}
const messages = ref<UiMessage[]>([])
const input = ref('')
const sending = ref(false)
const listEl = ref<HTMLElement>()

const anyAvailable = computed(() => models.value.some((m) => m.available))

/** 本地生成 sessionId（与 mall-ai normalizeSessionId 同风格：UUID 去横线） */
function genSid(): string {
  const raw = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  return raw.replace(/-/g, '')
}

/** 选中会话摘要（toolbar 展示用） */
const sessionLabel = computed(() => {
  if (!activeSid.value) return '新会话'
  const found = sessions.value.find((s) => s.sessionId === activeSid.value)
  return found ? found.preview.slice(0, 12) : '历史会话'
})

// ---------- 数据加载 ----------
async function loadModels() {
  try {
    models.value = await getAiConfig()
    const first = models.value.find((m) => m.available)
    provider.value = first?.provider || ''
  } catch {
    models.value = []
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await getAiSessions(scene)
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

/** 打开历史会话：拉取该会话消息（正序） */
async function openSession(sid: string) {
  if (sending.value) return
  activeSid.value = sid
  messages.value = []
  try {
    const history = await getAiMessages(scene, sid)
    messages.value = history.map((m) => ({ role: m.role, content: m.content }))
  } catch {
    ElMessage.error('历史消息加载失败')
  }
  scrollToBottom()
}

/** 新建会话：清空消息区，sid 在首次发送时生成 */
function newSession() {
  if (sending.value) return
  activeSid.value = null
  messages.value = []
  input.value = ''
}

// ---------- 发送 / 流式接收 ----------
async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return
  if (!anyAvailable.value) {
    ElMessage.warning('当前未配置任何模型的 API Key（docker/.env 配置后重启，见 README「AI 能力」）')
    return
  }
  sending.value = true
  if (!activeSid.value) activeSid.value = genSid()
  const sid = activeSid.value
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const aiMsg: UiMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(aiMsg)
  scrollToBottom()
  try {
    const full = await streamAiChat(
      { provider: provider.value || undefined, scene, sessionId: sid, message: question },
      (delta) => {
        aiMsg.content += delta
        scrollToBottom()
      }
    )
    aiMsg.content = full
    aiMsg.streaming = false
    loadSessions()
  } catch (e) {
    aiMsg.streaming = false
    aiMsg.content = (e as Error).message || 'AI 服务异常'
    ElMessage.error((e as Error).message || 'AI 服务异常')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function onKeydown(e: KeyboardEvent) {
  // Enter 发送 / Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

// ---------- 滚动 ----------
function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}
watch(() => messages.value.length, scrollToBottom)

onMounted(() => {
  loadModels()
  loadSessions()
})
</script>

<template>
  <div class="ai-page">
    <!-- 左侧：会话历史 -->
    <aside class="sessions">
      <el-button type="primary" class="new-btn" :disabled="sending" @click="newSession">
        <el-icon><Plus /></el-icon>&nbsp;新建会话
      </el-button>
      <el-scrollbar class="session-list">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === activeSid }"
          @click="openSession(s.sessionId)"
        >
          <p class="preview">{{ s.preview || '（空会话）' }}</p>
          <p class="meta">{{ s.total }} 条 · {{ s.createTime?.slice(5, 16) }}</p>
        </div>
        <el-empty v-if="!sessionsLoading && !sessions.length" description="暂无历史会话" :image-size="60" />
      </el-scrollbar>
    </aside>

    <!-- 右侧：聊天区 -->
    <section class="chat">
      <header class="chat-header">
        <div class="title">
          <el-icon class="ai-icon"><ChatDotRound /></el-icon>
          <span>AI 助手</span>
          <el-tag v-if="sessionLabel !== '新会话'" size="small" type="info">{{ sessionLabel }}</el-tag>
        </div>
        <div class="model-box">
          <span class="model-label">模型</span>
          <el-select v-model="provider" placeholder="自动选择" clearable class="model-select">
            <el-option
              v-for="m in models"
              :key="m.provider"
              :value="m.provider"
              :label="`${m.label}（${m.model}）`"
              :disabled="!m.available"
            />
          </el-select>
        </div>
      </header>

      <!-- 消息区 -->
      <div ref="listEl" class="msg-list">
        <el-empty
          v-if="!messages.length"
          description="向我提问：订单、库存、销量、会员运营等，例如「今天卖了多少？」"
          :image-size="90"
        />
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <span class="role-tag">{{ m.role === 'user' ? '我' : 'AI' }}</span>
            <span class="text">{{ m.content }}<i v-if="m.streaming" class="cursor" /></span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <footer class="input-bar">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="anyAvailable ? 'Enter 发送，Shift+Enter 换行' : '未配置模型 API Key，无法对话（见 README「AI 能力」）'"
          :disabled="sending || !anyAvailable"
          @keydown="onKeydown"
        />
        <div class="actions">
          <span class="tip">{{ sending ? 'AI 思考中…' : `${input.length}/2000` }}</span>
          <el-button type="primary" :loading="sending" :disabled="!anyAvailable || !input.trim()" @click="send">
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 106px);
}
.sessions {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.new-btn {
  margin-bottom: 12px;
}
.session-list {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  padding: 4px;
}
.session-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.session-item:hover {
  background: #f5f7fa;
}
.session-item.active {
  background: #ecf5ff;
}
.session-item .preview {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-item .meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eef0f3;
}
.chat-header .title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}
.ai-icon {
  color: #409eff;
  font-size: 18px;
}
.model-box {
  display: flex;
  align-items: center;
  gap: 8px;
}
.model-label {
  font-size: 13px;
  color: #666;
}
.model-select {
  width: 230px;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f7f8fa;
}
.msg {
  display: flex;
  margin-bottom: 14px;
}
.msg.user {
  justify-content: flex-end;
}
.msg .bubble {
  max-width: 76%;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.msg.user .bubble {
  flex-direction: row-reverse;
}
.role-tag {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  border-radius: 50%;
  font-size: 12px;
  color: #fff;
  background: #67c23a;
}
.msg.user .role-tag {
  background: #409eff;
}
.text {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  border: 1px solid #ebeef5;
}
.msg.user .text {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
.cursor {
  display: inline-block;
  width: 2px;
  height: 15px;
  margin-left: 3px;
  vertical-align: -2px;
  background: #409eff;
  animation: blink 0.8s infinite;
}
.msg.user .cursor {
  background: #fff;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.input-bar {
  padding: 12px 16px;
  border-top: 1px solid #eef0f3;
  background: #fff;
}
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.tip {
  font-size: 12px;
  color: #999;
}
</style>
