<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryPayment, type Payment } from '@/api/payment'

const route = useRoute()
const router = useRouter()
const orderSn = String(route.query.orderSn || '')
const payment = ref<Payment | null>(null)
const pollTimer = ref<number | null>(null)

/** 轮询支付结果（演示查单兜底：支付单成功但订单未回写时，查询接口会触发补偿回写） */
async function poll() {
  try {
    payment.value = await queryPayment(orderSn)
    if (payment.value?.status === 1) {
      stopPoll()
    }
  } catch {
    /* 查询异常继续轮询 */
  }
}

function stopPoll() {
  if (pollTimer.value !== null) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

onMounted(() => {
  poll()
  pollTimer.value = window.setInterval(poll, 3000)
})
onBeforeUnmount(stopPoll)
</script>

<template>
  <div class="pay-result">
    <van-nav-bar title="支付结果" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
    </van-nav-bar>

    <div v-if="payment" class="result-body">
      <van-icon
        :name="payment.status === 1 ? 'checked' : 'clock-o'"
        :color="payment.status === 1 ? '#07c160' : '#ff976a'"
        size="72"
        class="result-icon"
      />
      <h2 class="result-text">{{ payment.status === 1 ? '支付成功' : '等待支付' }}</h2>
      <p class="sub">{{ payment.status === 1 ? '订单已确认，商家将尽快发货' : '正在确认支付结果...' }}</p>

      <van-cell-group inset title="支付信息">
        <van-cell title="订单号" :value="payment.orderSn" />
        <van-cell title="支付流水号" :value="payment.paymentSn" />
        <van-cell title="交易号" :value="payment.tradeNo || '-'" />
        <van-cell title="支付金额" :value="`¥${Number(payment.payAmount).toFixed(2)}`" />
        <van-cell title="支付方式" :value="payment.payType === 1 ? '支付宝' : '微信支付'" />
        <van-cell v-if="payment.notifyTime" title="支付时间" :value="payment.notifyTime?.replace('T', ' ')" />
      </van-cell-group>

      <div class="actions">
        <van-button plain type="primary" round block @click="router.replace(`/order/${payment.orderSn}`)">
          查看订单
        </van-button>
        <van-button plain type="default" round block class="mt" @click="router.replace('/')">返回首页</van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pay-result {
  max-width: 640px;
  margin: 0 auto;
}
.result-body {
  padding-top: 60px;
  text-align: center;
}
.result-text {
  font-size: 20px;
  color: #323233;
  margin-top: 16px;
}
.sub {
  font-size: 13px;
  color: #969799;
  margin-top: 8px;
}
.actions {
  margin: 24px 40px;
}
.mt {
  margin-top: 12px;
}
</style>
