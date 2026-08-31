<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders, type OrderRow } from '@/api/order'

const router = useRouter()
const loading = ref(false)
const list = ref<OrderRow[]>([])
const total = ref(0)
const activeStatus = ref<number | undefined>(undefined)
const tabs = [
  { label: '全部', value: undefined },
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
  { label: '已退款', value: 5 }
]
const STATUS_TEXT = ['待付款', '待发货', '待收货', '已完成', '已取消', '已退款']

async function load() {
  loading.value = true
  try {
    const res = await getMyOrders(activeStatus.value, 1, 10)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="order-list">
    <van-nav-bar title="我的订单" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeStatus" @change="onTabChange">
      <van-tab v-for="t in tabs" :key="String(t.value)" :name="t.value" :title="t.label" />
    </van-tabs>

    <van-empty v-if="!loading && !list.length" description="暂无订单" />

    <div v-for="row in list" :key="row.order.id" class="order-card" @click="router.push(`/order/${row.order.orderSn}`)">
      <div class="card-head">
        <span class="sn">{{ row.order.orderSn }}</span>
        <span class="status" :class="`st-${row.order.status}`">{{ STATUS_TEXT[row.order.status] || '未知' }}</span>
      </div>
      <div class="goods-row" v-for="item in row.items" :key="item.id">
        <img class="thumb" :src="item.pic" />
        <div class="info">
          <p class="name">{{ item.spuName }}</p>
          <p v-if="item.spec" class="spec">{{ item.spec }}</p>
        </div>
        <div class="right">
          <p class="price">¥{{ Number(item.price).toFixed(2) }}</p>
          <p class="qty">x{{ item.quantity }}</p>
        </div>
      </div>
      <div class="card-foot">
        <span class="time">{{ row.order.createTime?.replace('T', ' ').slice(0, 16) }}</span>
        <span class="pay">
          实付：<span class="amount">¥{{ Number(row.order.payAmount).toFixed(2) }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-list {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 20px;
}
.order-card {
  background: #fff;
  margin: 12px;
  border-radius: 8px;
  overflow: hidden;
}
.card-head {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #f2f3f5;
}
.sn {
  font-size: 13px;
  color: #969799;
}
.status {
  font-size: 13px;
  color: #ee0a24;
  font-weight: 600;
}
.status.st-1,
.status.st-2 {
  color: #1989fa;
}
.status.st-3,
.status.st-4,
.status.st-5 {
  color: #969799;
}
.goods-row {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
}
.thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  background: #f2f3f5;
  object-fit: cover;
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.spec {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}
.right {
  text-align: right;
}
.price {
  font-size: 13px;
  color: #323233;
}
.qty {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-top: 1px solid #f2f3f5;
}
.time {
  font-size: 12px;
  color: #969799;
}
.pay {
  font-size: 13px;
  color: #969799;
}
.amount {
  color: #ee0a24;
  font-weight: 600;
  font-size: 15px;
}
</style>
