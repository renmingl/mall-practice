<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyRefunds, type RefundRow } from '@/api/payment'

const router = useRouter()
const loading = ref(false)
const list = ref<RefundRow[]>([])
const total = ref(0)

const STATUS_TEXT = ['申请中', '审核通过', '退货中', '退款中', '已退款', '已拒绝']
const STATUS_TAG = ['warning', 'primary', 'primary', 'primary', 'success', 'danger']

async function load() {
  loading.value = true
  try {
    const res = await getMyRefunds(1, 10)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="refund-list">
    <h2 class="page-title">我的退款单</h2>

    <van-empty v-if="!loading && !list.length" description="暂无退款记录" />

    <div v-for="row in list" :key="row.id" class="refund-card" @click="router.push(`/order/${row.orderSn}`)">
      <div class="head">
        <span class="sn">{{ row.refundSn }}</span>
        <van-tag :type="(STATUS_TAG[row.status] as any) || 'default'">
          {{ STATUS_TEXT[row.status] || '未知' }}
        </van-tag>
      </div>
      <div class="row">
        <span class="label">订单</span>
        <span class="value">{{ row.orderSn }}</span>
      </div>
      <div class="row">
        <span class="label">类型</span>
        <span class="value">{{ row.refundType === 2 ? '退货退款' : '仅退款' }}</span>
      </div>
      <div v-if="row.refundType === 2 && row.returnCompany" class="row">
        <span class="label">退货物流</span>
        <span class="value">{{ row.returnCompany }} {{ row.returnSn }}</span>
      </div>
      <div v-if="row.reason" class="row">
        <span class="label">原因</span>
        <span class="value">{{ row.reason }}</span>
      </div>
      <div class="foot">
        <span class="time">申请于 {{ row.applyTime?.replace('T', ' ').slice(0, 16) }}</span>
        <span class="amount">退款 ¥{{ Number(row.refundAmount).toFixed(2) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.refund-list {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 12px;
}
.refund-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #f2f3f5;
}
.sn {
  font-size: 13px;
  color: #969799;
}
.row {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-size: 13px;
}
.label {
  color: #969799;
  width: 60px;
  flex-shrink: 0;
}
.value {
  color: #323233;
  flex: 1;
  word-break: break-all;
}
.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f2f3f5;
}
.time {
  font-size: 12px;
  color: #c8c9cc;
}
.amount {
  font-size: 14px;
  color: #ee0a24;
  font-weight: 600;
}
</style>
