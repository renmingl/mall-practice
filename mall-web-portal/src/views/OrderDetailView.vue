<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  cancelOrder,
  confirmReceive,
  getOrderDetail,
  payOrder,
  type OrderDetail
} from '@/api/order'
import { getMyComments } from '@/api/comment'

const route = useRoute()
const router = useRouter()
const orderSn = String(route.params.orderSn)
const loading = ref(false)
const detail = ref<OrderDetail | null>(null)

const STATUS_TEXT = ['待付款', '待发货', '待收货', '已完成', '已取消', '已退款']
const order = computed(() => detail.value?.order)
const items = computed(() => detail.value?.items || [])
const statusLogs = computed(() => detail.value?.statusLogs || [])
const showLogs = ref(false)
/** 已评价的订单项 ID 集合（order_item 无 commented 列，拉取我的评价比对；已评价不显示评价按钮） */
const commentedItemIds = ref<Set<number>>(new Set())

async function load() {
  loading.value = true
  try {
    const [d, mine] = await Promise.all([getOrderDetail(orderSn), getMyComments(1, 100)])
    detail.value = d
    commentedItemIds.value = new Set(
      (mine.records || []).filter((c) => c.orderItemId).map((c) => c.orderItemId!)
    )
  } finally {
    loading.value = false
  }
}

/** 取消订单（待付款；回补库存/退券） */
async function onCancel() {
  await showConfirmDialog({ title: '提示', message: '确定取消该订单吗？库存与优惠券将回退' })
  await cancelOrder(orderSn)
  showToast('订单已取消')
  load()
}

/** 去支付（拉起收银台） */
async function onPay() {
  const payment = await payOrder(orderSn, 1)
  router.push(`/cashier?orderSn=${orderSn}&paymentSn=${payment.paymentSn}`)
}

/** 确认收货 */
async function onConfirmReceive() {
  await showConfirmDialog({ title: '提示', message: '确认已收到货？' })
  await confirmReceive(orderSn)
  showToast('确认收货成功')
  load()
}

/** 申请退款（跳转退款申请页：详情页内弹层，复用 RefundApply 组件逻辑 → 直接去退款页） */
function onApplyRefund() {
  router.push(`/refund/apply?orderSn=${orderSn}`)
}

onMounted(load)
</script>

<template>
  <div class="order-detail">
    <van-nav-bar title="订单详情" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
    </van-nav-bar>

    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <template v-else-if="detail">
      <!-- 状态横幅 -->
      <div class="status-banner">
        <p class="status-text">{{ STATUS_TEXT[order!.status] || '未知状态' }}</p>
        <p v-if="order!.status === 0" class="tip">请在 30 分钟内完成支付，超时自动关单</p>
        <p v-else-if="order!.status === 5" class="tip">该订单已整单退款</p>
      </div>

      <!-- 收货信息 -->
      <van-cell-group inset title="收货信息">
        <van-cell
          :title="`${order!.receiverName}  ${order!.receiverPhone}`"
          :label="order!.receiverAddress"
          icon="location-o"
        />
      </van-cell-group>

      <!-- 商品明细 -->
      <van-cell-group inset title="商品明细">
        <div v-for="item in items" :key="item.id" class="goods-row">
          <img class="thumb" :src="item.pic" />
          <div class="info">
            <p class="name">{{ item.spuName }}</p>
            <p v-if="item.spec" class="spec">{{ item.spec }}</p>
          </div>
          <div class="right">
            <p class="price">¥{{ Number(item.price).toFixed(2) }}</p>
            <p class="qty">x{{ item.quantity }}</p>
            <van-button
              v-if="order!.status === 3 && !commentedItemIds.has(item.id)"
              size="mini"
              type="primary"
              plain
              class="comment-btn"
              @click="router.push(`/comment?orderItemId=${item.id}&orderSn=${orderSn}`)"
            >
              评价
            </van-button>
          </div>
        </div>
      </van-cell-group>

      <!-- 金额与订单信息 -->
      <van-cell-group inset title="金额信息">
        <van-cell title="商品总额" :value="`¥${Number(order!.totalAmount).toFixed(2)}`" />
        <van-cell title="优惠券抵扣" :value="`-¥${Number(order!.discountAmount).toFixed(2)}`" />
        <van-cell title="实付金额" :value="`¥${Number(order!.payAmount).toFixed(2)}`" />
        <van-cell title="订单号" :value="order!.orderSn" />
        <van-cell title="下单时间" :value="order!.createTime?.replace('T', ' ')" />
        <van-cell v-if="order!.payTime" title="支付时间" :value="order!.payTime?.replace('T', ' ')" />
        <van-cell
          v-if="order!.status === 2 && order!.deliveryCompany"
          :title="`物流：${order!.deliveryCompany} ${order!.deliverySn}`"
        />
        <van-cell v-if="order!.cancelReason" title="取消原因" :value="order!.cancelReason" />
        <van-cell v-if="order!.remark" title="买家备注" :value="order!.remark" />
        <van-cell title="状态流水" is-link @click="showLogs = true" />
      </van-cell-group>

      <!-- 操作栏 -->
      <div v-if="order!.status === 0 || order!.status === 2 || (order!.status >= 1 && order!.status <= 3)" class="action-bar">
        <template v-if="order!.status === 0">
          <van-button plain type="default" round @click="onCancel">取消订单</van-button>
          <van-button type="danger" round @click="onPay">立即支付</van-button>
        </template>
        <template v-else-if="order!.status === 2">
          <van-button type="danger" round @click="onConfirmReceive">确认收货</van-button>
        </template>
        <template v-else-if="order!.status === 1 || order!.status === 3">
          <van-button plain type="warning" round @click="onApplyRefund">申请退款</van-button>
        </template>
      </div>

      <!-- 状态流水弹层 -->
      <van-popup v-model:show="showLogs" position="bottom" round>
        <div class="logs">
          <van-nav-bar title="订单状态流水" />
          <van-steps direction="vertical" :active="statusLogs.length - 1" active-color="#ee0a24">
            <van-step v-for="log in statusLogs" :key="log.id">
              <p class="log-line">{{ log.operator }}：{{ log.fromStatus }} → {{ log.toStatus }}</p>
              <p class="log-remark">{{ log.remark || '状态流转' }}</p>
              <p class="log-time">{{ log.createTime?.replace('T', ' ') }}</p>
            </van-step>
          </van-steps>
        </div>
      </van-popup>
    </template>
  </div>
</template>

<style scoped>
.order-detail {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 80px;
}
.page-loading {
  margin-top: 120px;
}
.status-banner {
  background: linear-gradient(135deg, #ee0a24, #ff6034);
  color: #fff;
  padding: 28px 20px;
}
.status-text {
  font-size: 22px;
  font-weight: 700;
}
.tip {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 8px;
}
.goods-row {
  display: flex;
  gap: 10px;
  padding: 10px 16px;
}
.thumb {
  width: 60px;
  height: 60px;
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
  font-size: 14px;
  color: #ee0a24;
  font-weight: 600;
}
.qty {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}
.comment-btn {
  margin-top: 6px;
}
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
.logs {
  padding-bottom: 20px;
  max-height: 60vh;
  overflow-y: auto;
}
.log-line {
  font-size: 14px;
  color: #323233;
}
.log-remark {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
}
.log-time {
  font-size: 12px;
  color: #c8c9cc;
  margin-top: 2px;
}
</style>
