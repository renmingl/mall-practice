<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { checkin, getCheckinStatus, type CheckinStatus } from '@/api/seckill'

const router = useRouter()

const status = ref<CheckinStatus | null>(null)
const loading = ref(false)

/** 当月天数（与后端 Bitmap 对齐：按当前自然月） */
const daysInMonth = computed(() => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
})

async function load() {
  loading.value = true
  try {
    status.value = await getCheckinStatus()
  } catch {
    status.value = null
  } finally {
    loading.value = false
  }
}

/** 签到（当天重复幂等，返回当月天数/连续天数） */
async function onCheckin() {
  try {
    const s = await checkin()
    status.value = s
    showToast(s.signedToday ? '签到成功' : '今天已签到')
  } catch {
    // 原因已由拦截器 toast
  }
}

onMounted(load)
</script>

<template>
  <div class="checkin-page">
    <h2 class="page-title">每日签到</h2>

    <div class="hero">
      <van-icon name="calendar-o" size="64" color="#ff976a" />
      <p class="hero-title">{{ status?.signedToday ? '今日已签到' : '今日尚未签到' }}</p>
      <p class="hero-sub">坚持签到，养成好习惯</p>
      <van-button
        round
        type="primary"
        size="large"
        :disabled="status?.signedToday"
        :loading="loading"
        class="checkin-btn"
        @click="onCheckin"
      >
        {{ status?.signedToday ? '已签到' : '立即签到' }}
      </van-button>
    </div>

    <!-- 签到统计 -->
    <van-cell-group inset title="本月统计">
      <van-cell title="本月签到" :value="`${status?.monthDays ?? 0} / ${daysInMonth} 天`" />
      <van-cell title="连续签到" :value="`${status?.streakDays ?? 0} 天`" />
    </van-cell-group>
  </div>
</template>

<style scoped>
.checkin-page {
  width: min(92vw, 1680px);
  margin: 0 auto;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.hero {
  margin: 24px 16px;
  padding: 32px 16px;
  text-align: center;
  background: #fff;
  border-radius: 8px;
}
.hero-title {
  margin: 12px 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.hero-sub {
  margin: 0 0 20px;
  color: #969799;
  font-size: 13px;
}
.checkin-btn {
  width: 160px;
}
</style>
