<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getMyCoupons, type MyCouponRow } from '@/api/coupon'

const loading = ref(false)
const list = ref<MyCouponRow[]>([])
const total = ref(0)
const activeStatus = ref<number | undefined>(undefined)
const tabs = [
  { label: '全部', value: undefined },
  { label: '未使用', value: 0 },
  { label: '已使用', value: 2 },
  { label: '已过期', value: 3 }
]

async function load() {
  loading.value = true
  try {
    const res = await getMyCoupons(activeStatus.value, 1, 50)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  load()
}

/** 券面额展示 */
function faceValue(row: MyCouponRow) {
  return row.type === 2 ? `${Number(row.amount) * 10}折` : `¥${Number(row.amount).toFixed(2)}`
}

onMounted(load)
</script>

<template>
  <div class="my-coupon">
    <h2 class="page-title">我的优惠券</h2>

    <van-tabs v-model:active="activeStatus" @change="onTabChange">
      <van-tab v-for="t in tabs" :key="String(t.value)" :name="t.value" :title="t.label" />
    </van-tabs>

    <van-empty v-if="!loading && !list.length" description="暂无优惠券" />

    <div v-for="row in list" :key="row.id" class="coupon-card" :class="`st-${row.status}`">
      <div class="left">
        <p class="value">{{ faceValue(row) }}</p>
        <p class="threshold">
          {{ Number(row.threshold) > 0 ? `满${Number(row.threshold).toFixed(2)}可用` : '无门槛' }}
        </p>
      </div>
      <div class="mid">
        <p class="name">{{ row.name }}</p>
        <p class="time">领取于 {{ row.receiveTime?.replace('T', ' ').slice(0, 16) }}</p>
        <p v-if="row.status === 0" class="time">
          有效期至 {{ row.expireTime?.replace('T', ' ').slice(0, 16) }}
        </p>
        <p v-if="row.status === 2 && row.orderSn" class="time">使用订单：{{ row.orderSn }}</p>
      </div>
      <div class="right">
        <van-tag :type="row.status === 0 ? 'success' : row.status === 2 ? 'primary' : 'default'">
          {{ ['未使用', '已锁定', '已使用', '已过期'][row.status] || '未知' }}
        </van-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.my-coupon {
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
.coupon-card.st-2,
.coupon-card.st-3 {
  opacity: 0.55;
}
.left {
  width: 110px;
  text-align: center;
  background: linear-gradient(135deg, #ee0a24, #ff6034);
  color: #fff;
  padding: 16px 8px;
}
.st-2 .left,
.st-3 .left {
  background: #c8c9cc;
}
.value {
  font-size: 18px;
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
.time {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.right {
  padding-right: 14px;
}
</style>
