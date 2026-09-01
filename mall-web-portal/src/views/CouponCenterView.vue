<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCouponCenter, receiveCoupon, type CouponTemplateRow } from '@/api/coupon'

const router = useRouter()
const loading = ref(false)
const list = ref<CouponTemplateRow[]>([])
const total = ref(0)
const page = ref(1)

async function load() {
  loading.value = true
  try {
    const res = await getCouponCenter(page.value, 10)
    list.value = page.value === 1 ? res.records : [...list.value, ...res.records]
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 领券 */
async function onReceive(row: CouponTemplateRow) {
  try {
    await receiveCoupon(row.id)
    showToast('领取成功')
    row.myReceived = (row.myReceived || 0) + 1
    row.receivable = row.receivable && (row.myReceived || 0) < row.perLimit
  } catch {
    /* 拦截器已提示 */
  }
}

/** 券面额展示：满减 → ¥X；折扣 → X 折 */
function faceValue(row: CouponTemplateRow) {
  return row.type === 2 ? `${Number(row.amount) * 10}折` : `¥${Number(row.amount).toFixed(2)}`
}

/** 门槛展示 */
function thresholdText(row: CouponTemplateRow) {
  return Number(row.threshold) > 0 ? `满${Number(row.threshold).toFixed(2)}元可用` : '无门槛'
}

onMounted(load)
</script>

<template>
  <div class="coupon-center">
    <h2 class="page-title">领券中心<span class="page-title-link"><span class="nav-link" @click="router.push('/my-coupons')">我的券</span></span></h2>

    <van-empty v-if="!loading && !list.length" description="暂无进行中的优惠券活动" />

    <div v-for="row in list" :key="row.id" class="coupon-card">
      <div class="left">
        <p class="value">{{ faceValue(row) }}</p>
        <p class="threshold">{{ thresholdText(row) }}</p>
      </div>
      <div class="mid">
        <p class="name">{{ row.name }}</p>
        <p class="time">有效期至 {{ row.useEndTime?.replace('T', ' ') }}</p>
        <p class="remain">剩余 {{ row.remaining ?? 0 }} 张 · 每人限领 {{ row.perLimit }} 张</p>
      </div>
      <div class="right">
        <van-button
          size="small"
          round
          :type="row.receivable ? 'danger' : 'default'"
          :disabled="!row.receivable"
          @click="onReceive(row)"
        >
          {{ row.receivable ? '立即领取' : '已领完' }}
        </van-button>
      </div>
    </div>

    <van-button
      v-if="list.length < total"
      block
      plain
      type="primary"
      class="load-more"
      :loading="loading"
      @click="page++; load()"
    >
      加载更多
    </van-button>
  </div>
</template>

<style scoped>
.coupon-center {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 12px;
}
.coupon-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.left {
  width: 110px;
  text-align: center;
  background: linear-gradient(135deg, #ee0a24, #ff6034);
  color: #fff;
  padding: 18px 8px;
}
.value {
  font-size: 20px;
  font-weight: 700;
}
.threshold {
  font-size: 11px;
  opacity: 0.9;
  margin-top: 4px;
}
.mid {
  flex: 1;
  padding: 12px 14px;
  min-width: 0;
}
.name {
  font-size: 15px;
  color: #323233;
  font-weight: 600;
}
.time,
.remain {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}
.right {
  padding-right: 12px;
}
.load-more {
  margin-top: 8px;
}
</style>
