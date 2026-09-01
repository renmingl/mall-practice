<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import {
  getSeckillProducts,
  getSeckillSessions,
  getSeckillToken,
  querySeckillResult,
  submitSeckill,
  type SeckillProductItem,
  type SeckillSession
} from '@/api/seckill'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const sessions = ref<SeckillSession[]>([])
const activeSession = ref<number>()
const products = ref<SeckillProductItem[]>([])
const loading = ref(false)
const buyingId = ref<number>()

// 抢购：收货信息弹窗
const showReceiver = ref(false)
const submitting = ref(false)
const currentProduct = ref<SeckillProductItem | null>(null)
const receiverForm = ref({ name: '', phone: '', address: '' })

const currentSession = computed(() => sessions.value.find((s) => s.id === activeSession.value))

const PHASE_TEXT: Record<string, string> = { disabled: '已禁用', upcoming: '未开始', ongoing: '进行中', finished: '已结束' }

function fmtTime(t?: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

/** 场次是否可抢（进行中） */
function canBuy(p: SeckillProductItem) {
  return currentSession.value?.phase === 'ongoing'
}

async function loadSessions() {
  try {
    sessions.value = await getSeckillSessions()
    // 默认选中进行中场次，否则第一个
    const ongoing = sessions.value.find((s) => s.phase === 'ongoing')
    activeSession.value = ongoing?.id ?? sessions.value[0]?.id
    if (activeSession.value) {
      loadProducts(activeSession.value)
    }
  } catch {
    showToast('秒杀场次加载失败，请确认 mall-seckill 已启动')
  }
}

async function loadProducts(sessionId: number) {
  loading.value = true
  try {
    products.value = await getSeckillProducts(sessionId)
  } catch {
    products.value = []
  } finally {
    loading.value = false
  }
}

function onSessionChange(id: number | string) {
  if (typeof id === 'number' && id) {
    loadProducts(id)
  }
}

/** 点击抢购：登录校验 → 弹收货信息（token 在提交时获取） */
function onBuy(p: SeckillProductItem) {
  if (!userStore.isLoggedIn) {
    showToast('请先登录')
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (p.remainStock <= 0) {
    showToast('手慢了，商品已抢光')
    return
  }
  currentProduct.value = p
  receiverForm.value = { name: '', phone: '', address: '' }
  showReceiver.value = true
}

/** 提交秒杀：发放 token → Lua 扣减 + MQ 削峰 → 轮询结果 */
async function onSubmit() {
  const p = currentProduct.value
  if (!p) return
  if (!receiverForm.value.name || !receiverForm.value.phone || !receiverForm.value.address) {
    showToast('请填写完整的收货信息')
    return
  }
  submitting.value = true
  buyingId.value = p.id
  try {
    const { token } = await getSeckillToken()
    await submitSeckill({
      seckillProductId: p.id,
      quantity: 1,
      token,
      receiverName: receiverForm.value.name,
      receiverPhone: receiverForm.value.phone,
      receiverAddress: receiverForm.value.address
    })
    showReceiver.value = false
    showToast('抢购成功，正在为您下单…')
    pollResult(p.id)
  } catch {
    buyingId.value = undefined
    // 失败原因已由拦截器 toast（超卖/限购/令牌失效等）
  } finally {
    submitting.value = false
  }
}

/** 结果轮询（14.6）：0 处理中 / 1 成功跳订单 / 2 失败提示，最多 20 秒 */
function pollResult(pid: number) {
  let times = 0
  const timer = setInterval(async () => {
    times++
    try {
      const r = await querySeckillResult(pid)
      if (r.status === 1 && r.orderSn) {
        clearInterval(timer)
        buyingId.value = undefined
        router.push(`/order/${r.orderSn}`)
        return
      }
      if (r.status === 2) {
        clearInterval(timer)
        buyingId.value = undefined
        showToast(r.reason || '下单失败，已为您回补库存')
        if (activeSession.value) loadProducts(activeSession.value)
        return
      }
      if (times >= 10) {
        clearInterval(timer)
        buyingId.value = undefined
        showToast('下单处理中，请稍后在订单列表查看')
      }
    } catch {
      // 单次轮询失败不中断，继续等待
    }
  }, 2000)
}

onMounted(loadSessions)
</script>

<template>
  <div class="seckill-page">
    <van-nav-bar title="限时秒杀" fixed placeholder>
      <template #left>
        <span class="nav-link" @click="router.push('/')">首页</span>
      </template>
      <template #right>
        <span class="nav-link" @click="router.push('/rank')">排行</span>
      </template>
    </van-nav-bar>

    <van-notice-bar left-icon="volume-o" text="秒杀库存有限先到先得，每人限购以场次配置为准；下单结果稍候查询" />

    <!-- 场次切换 -->
    <van-tabs v-model:active="activeSession" @change="onSessionChange">
      <van-tab v-for="s in sessions" :key="s.id" :name="s.id">
        <template #title>
          <span>{{ s.name }}</span>
          <span class="phase" :class="s.phase">{{ PHASE_TEXT[s.phase ?? ''] ?? '' }}</span>
        </template>
      </van-tab>
    </van-tabs>

    <p v-if="currentSession" class="session-time">
      {{ fmtTime(currentSession.startTime) }} ~ {{ fmtTime(currentSession.endTime) }}
    </p>

    <!-- 商品列表 -->
    <van-loading v-if="loading" class="page-loading" />
    <van-empty v-else-if="!products.length" description="该场次暂无秒杀商品" />
    <van-card v-for="p in products" :key="p.id" class="sk-card" :title="p.spuName" :desc="p.spec" :thumb="p.pic">
      <template #price>
        <span class="sk-price">¥{{ p.seckillPrice.toFixed(2) }}</span>
        <span class="sk-origin">¥{{ p.price.toFixed(2) }}</span>
      </template>
      <template #num>
        <span class="sk-stock" :class="{ low: p.remainStock < 10 }">剩 {{ p.remainStock }} 件</span>
      </template>
      <template #tags>
        <van-tag type="danger" plain>限购 {{ p.limitPerUser }} 件</van-tag>
      </template>
      <template #footer>
        <div class="sk-footer">
          <span class="sk-note">秒杀价不与优惠券叠加</span>
          <van-button
            size="small"
            round
            type="danger"
            :loading="buyingId === p.id"
            :disabled="!canBuy(p) || p.remainStock <= 0"
            @click="onBuy(p)"
          >
            {{ p.remainStock <= 0 ? '已抢光' : canBuy(p) ? '立即抢购' : '未开始' }}
          </van-button>
        </div>
      </template>
    </van-card>

    <!-- 收货信息弹窗 -->
    <van-popup v-model:show="showReceiver" position="bottom" round>
      <van-form @submit="onSubmit">
        <van-cell-group inset title="填写收货信息">
          <van-field
            v-model="receiverForm.name"
            name="name"
            label="收货人"
            placeholder="请输入收货人姓名"
            :rules="[{ required: true, message: '请输入收货人' }]"
          />
          <van-field
            v-model="receiverForm.phone"
            name="phone"
            label="手机号"
            type="tel"
            placeholder="请输入手机号"
            :rules="[{ required: true, message: '请输入手机号' }]"
          />
          <van-field
            v-model="receiverForm.address"
            name="address"
            label="收货地址"
            placeholder="请输入详细地址"
            :rules="[{ required: true, message: '请输入地址' }]"
          />
        </van-cell-group>
        <div style="margin: 16px">
          <van-button round block type="danger" native-type="submit" :loading="submitting">确认抢购</van-button>
        </div>
      </van-form>
    </van-popup>
  </div>
</template>

<style scoped>
.seckill-page {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 24px;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.phase {
  display: block;
  font-size: 10px;
  color: #969799;
  transform: scale(0.9);
}
.phase.ongoing {
  color: #ee0a24;
}
.session-time {
  margin: 8px 16px;
  color: #969799;
  font-size: 12px;
}
.page-loading {
  padding: 40px 0;
}
.sk-card {
  margin-bottom: 8px;
}
.sk-price {
  color: #ee0a24;
  font-size: 18px;
  font-weight: 600;
}
.sk-origin {
  margin-left: 6px;
  color: #969799;
  font-size: 12px;
  text-decoration: line-through;
}
.sk-stock {
  color: #ff976a;
  font-size: 12px;
}
.sk-stock.low {
  color: #ee0a24;
  font-weight: 600;
}
.sk-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sk-note {
  color: #969799;
  font-size: 12px;
}
</style>
