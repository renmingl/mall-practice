<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { payOrder } from '@/api/order'
import { mockPayCallback, queryPayment, type Payment } from '@/api/payment'

const route = useRoute()
const router = useRouter()
const orderSn = String(route.query.orderSn || '')
const loading = ref(false)
const paying = ref(false)
const payType = ref(1)
const payment = ref<Payment | null>(null)

const PAY_TYPES = [
  { value: 1, label: '支付宝', icon: 'alipay' },
  { value: 2, label: '微信支付', icon: 'wechat-pay' }
]
const payTypeLabel = computed(() => PAY_TYPES.find((t) => t.value === payType.value)?.label || '')

/** 创建支付流水（幂等复用） */
async function ensurePayment() {
  if (!orderSn) {
    showToast('缺少订单号')
    router.replace('/orders')
    return
  }
  loading.value = true
  try {
    payment.value = await payOrder(orderSn, payType.value)
  } finally {
    loading.value = false
  }
}

/** 模拟支付（演示环境：点击即回调成功；生产由第三方支付拉起收银台 + 异步通知） */
async function onPay() {
  if (!payment.value) return
  paying.value = true
  try {
    const result = await mockPayCallback(payment.value.paymentSn)
    showToast('支付成功')
    router.replace(`/pay-result?orderSn=${orderSn}`)
  } catch {
    // 拦截器已提示（如订单状态已变化）
  } finally {
    paying.value = false
  }
}

onMounted(ensurePayment)
</script>

<template>
  <div class="cashier">
    <h2 class="page-title">收银台</h2>

    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <template v-else-if="payment">
      <!-- 金额 -->
      <div class="amount-bar">
        <p class="label">需支付金额（¥）</p>
        <p class="amount">{{ Number(payment.payAmount).toFixed(2) }}</p>
        <p class="order-sn">订单号：{{ payment.orderSn }}</p>
      </div>

      <!-- 支付方式 -->
      <van-cell-group inset title="选择支付方式">
        <van-cell
          v-for="t in PAY_TYPES"
          :key="t.value"
          :title="t.label"
          icon="gold-coin-o"
          is-link
          :class="{ active: payType === t.value }"
          @click="payType = t.value"
        >
          <template #right-icon>
            <van-icon v-if="payType === t.value" name="success" color="#ee0a24" />
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="说明">
        <van-cell title="本环境为演示环境" label="点击立即支付将模拟第三方支付回调（trade_no 幂等），支付成功后订单自动流转" />
      </van-cell-group>

      <div class="pay-bar">
        <van-button type="danger" round block :loading="paying" @click="onPay">
          立即支付 {{ payTypeLabel }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cashier {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding-bottom: 80px;
}
.page-loading {
  margin-top: 120px;
}
.amount-bar {
  text-align: center;
  padding: 40px 0;
}
.label {
  font-size: 14px;
  color: #969799;
}
.amount {
  font-size: 44px;
  font-weight: 700;
  color: #323233;
  margin-top: 8px;
}
.order-sn {
  font-size: 12px;
  color: #c8c9cc;
  margin-top: 8px;
}
.cell.active {
  background: #fff7f8;
}
.pay-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 12px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
</style>
