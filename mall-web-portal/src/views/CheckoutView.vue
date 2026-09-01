<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getAddressList, type MemberAddress } from '@/api/member'
import { getCheckoutPreview, type CheckoutCoupon, type CheckoutPreview } from '@/api/checkout'
import { createOrder } from '@/api/order'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const preview = ref<CheckoutPreview | null>(null)
const addresses = ref<MemberAddress[]>([])
const selectedCoupon = ref<CheckoutCoupon | null>(null)
const couponPickerShow = ref(false)
const remark = ref('')

/** 默认地址（无默认取第一条） */
const defaultAddress = computed(
  () => addresses.value.find((a) => a.defaultFlag === 1) || addresses.value[0] || null
)

/** 商品总额 */
const totalAmount = computed(() => Number(preview.value?.totalAmount || 0))
/** 券抵扣 */
const discountAmount = computed(() => Number(selectedCoupon.value?.discountAmount || 0))
/** 实付 */
const payAmount = computed(() => Math.max(totalAmount.value - discountAmount.value, 0))

async function load() {
  loading.value = true
  try {
    const [p, addr] = await Promise.all([getCheckoutPreview(), getAddressList()])
    preview.value = p
    addresses.value = addr
  } finally {
    loading.value = false
  }
}

function onSelectCoupon(coupon: CheckoutCoupon | null) {
  selectedCoupon.value = coupon
  couponPickerShow.value = false
}

/** 提交订单：requestId 幂等（前端 uuid），失败可直接重试 */
async function onSubmit() {
  if (!defaultAddress.value) {
    showToast('请先添加收货地址')
    router.push('/address')
    return
  }
  if (!preview.value?.items.some((i) => i.valid)) {
    showToast('没有可结算的商品')
    return
  }
  submitting.value = true
  try {
    const addr = defaultAddress.value
    const order = await createOrder({
      requestId: crypto.randomUUID().replace(/-/g, ''),
      couponUserId: selectedCoupon.value?.couponUserId,
      receiverName: addr.receiverName,
      receiverPhone: addr.receiverPhone,
      receiverAddress: [addr.province, addr.city, addr.district, addr.detailAddress].filter(Boolean).join(' '),
      remark: remark.value
    })
    showToast('下单成功')
    router.replace(`/cashier?orderSn=${order.orderSn}`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="checkout">
    <h2 class="page-title">确认订单</h2>

    <van-loading v-if="loading" class="page-loading" vertical>加载中...</van-loading>

    <template v-else-if="preview">
      <!-- 收货地址 -->
      <div class="addr-card" @click="router.push('/address')">
        <template v-if="defaultAddress">
          <div class="addr-main">
            <p class="addr-line">
              <span class="receiver">{{ defaultAddress.receiverName }}</span>
              <span class="phone">{{ defaultAddress.receiverPhone }}</span>
              <van-tag v-if="defaultAddress.defaultFlag === 1" type="danger" plain>默认</van-tag>
            </p>
            <p class="addr-detail">
              {{ [defaultAddress.province, defaultAddress.city, defaultAddress.district].filter(Boolean).join(' ') }}
              {{ defaultAddress.detailAddress }}
            </p>
          </div>
          <van-icon name="arrow" />
        </template>
        <div v-else class="addr-empty">
          <van-icon name="add-o" />
          <span>请添加收货地址</span>
        </div>
      </div>

      <!-- 商品清单 -->
      <van-cell-group inset title="商品清单">
        <div v-for="item in preview.items.filter((i) => i.valid)" :key="item.skuId" class="goods-row">
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
        <van-empty v-if="!preview.items.some((i) => i.valid)" description="没有可结算的商品" image-size="60" />
      </van-cell-group>

      <!-- 优惠券 -->
      <van-cell-group inset>
        <van-cell
          title="优惠券"
          :value="selectedCoupon ? `-¥${discountAmount.toFixed(2)}` : preview.availableCoupons.length ? `${preview.availableCoupons.length}张可用` : '暂无可用'"
          is-link
          @click="couponPickerShow = true"
        />
        <van-cell title="买家备注">
          <template #value>
            <input v-model="remark" class="remark-input" placeholder="选填" maxlength="50" />
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 金额明细 -->
      <van-cell-group inset title="金额明细">
        <van-cell title="商品总额" :value="`¥${totalAmount.toFixed(2)}`" />
        <van-cell title="优惠券抵扣" :value="`-¥${discountAmount.toFixed(2)}`" />
        <van-cell title="运费" value="¥0.00" />
      </van-cell-group>

      <!-- 提交栏 -->
      <div class="submit-bar">
        <div class="pay-amount">
          <span>实付：</span>
          <span class="amount">¥{{ payAmount.toFixed(2) }}</span>
        </div>
        <van-button type="danger" round :loading="submitting" @click="onSubmit">提交订单</van-button>
      </div>

      <!-- 优惠券选择弹层 -->
      <van-popup v-model:show="couponPickerShow" position="bottom" round>
        <div class="picker">
          <van-nav-bar title="选择优惠券">
            <template #right>
              <span class="nav-link" @click="onSelectCoupon(null)">不使用</span>
            </template>
          </van-nav-bar>
          <div v-for="c in preview.availableCoupons" :key="c.couponUserId" class="coupon-option" @click="onSelectCoupon(c)">
            <div class="left">
              <p class="value">{{ c.type === 2 ? `${Number(c.amount) * 10}折` : `¥${Number(c.amount).toFixed(2)}` }}</p>
              <p class="threshold">{{ Number(c.threshold) > 0 ? `满${Number(c.threshold).toFixed(2)}可用` : '无门槛' }}</p>
            </div>
            <div class="mid">
              <p class="name">{{ c.name }}</p>
              <p class="discount">可抵扣 ¥{{ Number(c.discountAmount).toFixed(2) }}</p>
            </div>
            <van-icon v-if="selectedCoupon?.couponUserId === c.couponUserId" name="success" color="#ee0a24" />
          </div>
          <van-empty v-if="!preview.availableCoupons.length" description="暂无可用优惠券" image-size="60" />
        </div>
      </van-popup>
    </template>
  </div>
</template>

<style scoped>
.checkout {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding-bottom: 70px;
}
.page-loading {
  margin-top: 120px;
}
.addr-card {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 12px;
  padding: 14px;
  border-radius: 8px;
}
.addr-main {
  flex: 1;
  min-width: 0;
}
.addr-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.receiver {
  font-size: 15px;
  font-weight: 600;
}
.phone {
  font-size: 13px;
  color: #969799;
}
.addr-detail {
  font-size: 13px;
  color: #646566;
  margin-top: 6px;
}
.addr-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #969799;
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
.remark-input {
  border: none;
  outline: none;
  text-align: right;
  font-size: 14px;
  color: #323233;
  width: 120px;
}
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  width: min(92vw, 1680px);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
.pay-amount {
  font-size: 13px;
  color: #969799;
}
.amount {
  font-size: 18px;
  color: #ee0a24;
  font-weight: 600;
}
.picker {
  max-height: 60vh;
  overflow-y: auto;
}
.coupon-option {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 12px;
  padding: 12px;
  border: 1px solid #ebedf0;
  border-radius: 8px;
}
.coupon-option .left {
  width: 90px;
  text-align: center;
  color: #ee0a24;
}
.coupon-option .value {
  font-size: 16px;
  font-weight: 700;
}
.coupon-option .threshold {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}
.coupon-option .mid {
  flex: 1;
  min-width: 0;
}
.coupon-option .name {
  font-size: 14px;
  color: #323233;
}
.coupon-option .discount {
  font-size: 12px;
  color: #ee0a24;
  margin-top: 4px;
}
</style>
