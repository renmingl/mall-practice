<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { applyRefund } from '@/api/payment'
import { getOrderDetail } from '@/api/order'

const route = useRoute()
const router = useRouter()
const orderSn = String(route.query.orderSn || '')
const loading = ref(false)
const submitting = ref(false)
const orderAmount = ref(0)

const refundType = ref<number>(1)
const reason = ref('')
const returnCompany = ref('')
const returnSn = ref('')

const REFUND_TYPES = [
  { value: 1, label: '仅退款', desc: '未收到货/不想要了（审核通过即退款）' },
  { value: 2, label: '退货退款', desc: '已收到货需退货（需填写退货物流）' }
]

async function load() {
  if (!orderSn) {
    showToast('缺少订单号')
    router.replace('/orders')
    return
  }
  loading.value = true
  try {
    const detail = await getOrderDetail(orderSn)
    orderAmount.value = Number(detail.order.payAmount)
  } finally {
    loading.value = false
  }
}

async function onSubmit() {
  if (refundType.value === 2 && (!returnCompany.value.trim() || !returnSn.value.trim())) {
    showToast('退货退款须填写退货物流公司与单号')
    return
  }
  submitting.value = true
  try {
    await applyRefund({
      orderSn,
      reason: reason.value,
      refundType: refundType.value,
      returnCompany: returnCompany.value.trim() || undefined,
      returnSn: returnSn.value.trim() || undefined
    })
    showToast('退款申请已提交')
    router.replace('/refunds')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="refund-apply">
    <van-nav-bar title="申请退款" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
    </van-nav-bar>

    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <template v-else>
      <van-cell-group inset title="退款类型">
        <van-cell
          v-for="t in REFUND_TYPES"
          :key="t.value"
          :title="t.label"
          :label="t.desc"
          is-link
          @click="refundType = t.value"
        >
          <template #right-icon>
            <van-icon v-if="refundType === t.value" name="success" color="#ee0a24" />
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="退款金额">
        <van-cell title="可退金额（整单）" :value="`¥${orderAmount.toFixed(2)}`" />
      </van-cell-group>

      <van-cell-group inset title="退款原因">
        <van-field v-model="reason" rows="2" type="textarea" maxlength="200" placeholder="请填写退款原因（选填）" />
      </van-cell-group>

      <van-cell-group v-if="refundType === 2" inset title="退货物流（须填）">
        <van-field v-model="returnCompany" placeholder="物流公司，如：顺丰速运" />
        <van-field v-model="returnSn" placeholder="物流单号" />
      </van-cell-group>

      <div class="submit-bar">
        <van-button type="danger" round block :loading="submitting" @click="onSubmit">提交申请</van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.refund-apply {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 80px;
}
.page-loading {
  margin-top: 120px;
}
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 640px;
  margin: 0 auto;
  padding: 12px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
</style>
