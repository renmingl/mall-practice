<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import {
  getAiConfig, getAiMessages, getAiSessions, streamAiChat,
  type AiModelInfo
} from '@/api/ai'

// AI 客服浮窗（阶段 9 16.3）：游客普通问答；登录会员自动续接最近会话并可按需查询本人数据
// scene=portal：登录态由网关注入 X-User-Id/X-User-Type，游客无头 → 后端能力分层只答通用问题

const userStore = useUserStore()

const open = ref(false)
const models = ref<AiModelInfo[]>([])
const sending = ref(false)
const input = ref('')
const activeSid = ref<string | null>(null)

interface UiMessage {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}
const messages = ref<UiMessage[]>([])
const listEl = ref<HTMLElement>()

const anyAvailable = computed(() => models.value.some((m) => m.available))

/** 快捷问题：游客只给通用问题；登录会员给本人数据问题（演示能力分层） */
const quickQuestions = computed(() =>
  userStore.isLoggedIn
    ? ['我的优惠券', '我的最近订单', '我的账户信息']
    : ['这个项目是做什么的？', '如何部署运行？', '有哪些功能模块？']
)

/** 欢迎语：游客说明能力边界并引导登录；会员说明可查本人数据 */
const welcomeText = computed(() =>
  userStore.isLoggedIn
    ? `你好，${userStore.nickname}！我是 AI 客服，可以帮你查询优惠券、积分、最近订单、购物车等本人数据，也可以解答商品与项目问题。`
    : '你好，我是 AI 客服，可以解答项目、商品、下单等问题。登录后我还能帮你查询优惠券、订单、积分等个人数据哦。'
)

function genSid(): string {
  const raw = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  return raw.replace(/-/g, '')
}

function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}
watch(() => messages.value.length, scrollToBottom)

/** 打开面板：加载模型清单；登录会员续接最近会话 */
async function togglePanel() {
  open.value = !open.value
  if (!open.value || messages.value.length) return
  if (!models.value.length) {
    try {
      models.value = await getAiConfig()
    } catch {
      models.value = []
    }
  }
  messages.value.push({ role: 'assistant', content: welcomeText.value })
  if (!userStore.isLoggedIn) return
  try {
    const list = await getAiSessions()
    const latest = list[0]
    if (!latest) return
    activeSid.value = latest.sessionId
    const history = await getAiMessages(latest.sessionId)
    messages.value.push(...history.map((m) => ({ role: m.role, content: m.content })))
    scrollToBottom()
  } catch {
    // 无历史/接口异常：保持欢迎语即可
  }
}

async function send(text?: string) {
  const question = (text ?? input.value).trim()
  if (!question || sending.value) return
  if (!anyAvailable.value) {
    showToast('AI 模型未配置 API Key，暂不可用')
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
      { scene: 'portal', sessionId: sid, message: question },
      (delta) => {
        aiMsg.content += delta
        scrollToBottom()
      }
    )
    aiMsg.content = full
    aiMsg.streaming = false
  } catch (e) {
    aiMsg.streaming = false
    aiMsg.content = (e as Error).message || 'AI 服务异常，请稍后再试'
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

onMounted(() => {
  // 预加载模型清单（面板打开时直接可用，无感）
  getAiConfig()
    .then((list) => (models.value = list))
    .catch(() => {})
})
</script>

<template>
  <!-- 右下角客服浮窗（登录/注册页不展示：由 App.vue 控制挂载） -->
  <div class="cs-root">
    <!-- 悬浮入口按钮 -->
    <button v-if="!open" class="cs-entry" aria-label="AI 客服" @click="togglePanel">
      <van-icon name="service-o" size="24" />
      <span class="cs-badge" />
    </button>

    <!-- 聊天面板 -->
    <section v-else class="cs-panel">
      <header class="cs-head">
        <div class="cs-title">
          <van-icon name="service-o" size="18" />
          <span>AI 客服</span>
          <em class="cs-role">{{ userStore.isLoggedIn ? '会员' : '游客' }}</em>
        </div>
        <button class="cs-close" aria-label="关闭" @click="open = false">
          <van-icon name="cross" />
        </button>
      </header>

      <!-- 消息区 -->
      <div ref="listEl" class="cs-body">
        <div v-for="(m, i) in messages" :key="i" class="cs-msg" :class="m.role">
          <span class="cs-avatar">{{ m.role === 'user' ? '我' : 'AI' }}</span>
          <div class="cs-bubble">
            <p class="cs-text">{{ m.content }}<i v-if="m.streaming" class="cs-cursor" /></p>
          </div>
        </div>
      </div>

      <!-- 快捷问题 -->
      <div v-if="!messages.some((m) => m.role === 'user')" class="cs-quick">
        <button v-for="q in quickQuestions" :key="q" :disabled="sending" @click="send(q)">
          {{ q }}
        </button>
      </div>

      <!-- 输入区 -->
      <footer class="cs-foot">
        <textarea
          v-model="input"
          rows="1"
          maxlength="500"
          :placeholder="anyAvailable ? '输入问题，Enter 发送' : 'AI 模型未配置，暂不可用'"
          :disabled="sending || !anyAvailable"
          @keydown.enter.exact.prevent="send()"
        />
        <button class="cs-send" :disabled="sending || !anyAvailable || !input.trim()" @click="send()">
          <van-icon name="arrow-up" size="16" />
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.cs-root {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 999;
  font-family: inherit;
}
/* 悬浮入口 */
.cs-entry {
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 50%;
  background: #1989fa;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(25, 137, 250, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s;
}
.cs-entry:hover {
  transform: scale(1.06);
}
.cs-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ee0a24;
  border: 2px solid #fff;
}
/* 面板 */
.cs-panel {
  width: 340px;
  height: 480px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.cs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #1989fa;
  color: #fff;
}
.cs-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}
.cs-role {
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  padding: 1px 8px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.25);
}
.cs-close {
  border: none;
  background: transparent;
  color: #fff;
  cursor: pointer;
  font-size: 16px;
  padding: 2px;
}
/* 消息区 */
.cs-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #f7f8fa;
}
.cs-msg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.cs-msg.user {
  flex-direction: row-reverse;
}
.cs-avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  line-height: 30px;
  text-align: center;
  font-size: 12px;
  color: #fff;
  background: #07c160;
}
.cs-msg.user .cs-avatar {
  background: #1989fa;
}
.cs-bubble {
  max-width: 76%;
}
.cs-text {
  margin: 0;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  border: 1px solid #ebedf0;
  color: #323233;
}
.cs-msg.user .cs-text {
  background: #1989fa;
  border-color: #1989fa;
  color: #fff;
}
.cs-cursor {
  display: inline-block;
  width: 2px;
  height: 13px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #1989fa;
  animation: cs-blink 0.8s infinite;
}
.cs-msg.user .cs-cursor {
  background: #fff;
}
@keyframes cs-blink {
  50% {
    opacity: 0;
  }
}
/* 快捷问题 */
.cs-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #f2f3f5;
  background: #fff;
}
.cs-quick button {
  border: 1px solid #1989fa;
  color: #1989fa;
  background: #fff;
  border-radius: 14px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}
.cs-quick button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
/* 输入区 */
.cs-foot {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f2f3f5;
}
.cs-foot textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 13px;
  line-height: 20px;
  max-height: 80px;
  background: transparent;
}
.cs-send {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: #1989fa;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cs-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
