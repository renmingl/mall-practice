<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  checkCartItems,
  getCartList,
  removeCartItems,
  updateCartQuantity,
  type CartRow
} from '@/api/cart'

const router = useRouter()
const loading = ref(false)
const rows = ref<CartRow[]>([])

/** 有效条目（未失效） */
const validRows = computed(() => rows.value.filter((r) => !r.invalid))
/** 全部可操作条目是否全选 */
const allChecked = computed(
  () => validRows.value.length > 0 && validRows.value.every((r) => r.checked)
)
/** 勾选条目 */
const checkedRows = computed(() => validRows.value.filter((r) => r.checked))
/** 勾选合计 */
const totalAmount = computed(() =>
  checkedRows.value.reduce((sum, r) => sum + (r.subtotal || 0), 0)
)

async function load() {
  loading.value = true
  try {
    rows.value = await getCartList()
  } finally {
    loading.value = false
  }
}

/** 勾选/取消勾选单条 */
async function toggleChecked(row: CartRow) {
  await checkCartItems([row.skuId], !row.checked)
  row.checked = !row.checked
}

/** 全选/取消全选 */
async function toggleAll() {
  const target = !allChecked.value
  await checkCartItems(
    validRows.value.map((r) => r.skuId),
    target
  )
  validRows.value.forEach((r) => (r.checked = target))
}

/** 数量变更 */
async function onQuantityChange(row: CartRow) {
  if (row.quantity < 1) {
    row.quantity = 1
    return
  }
  await updateCartQuantity(row.skuId, row.quantity)
  row.subtotal = (row.price || 0) * row.quantity
}

/** 删除（支持多选删除） */
async function onRemove(row?: CartRow) {
  const target = row ? [row.skuId] : checkedRows.value.map((r) => r.skuId)
  if (!target.length) {
    showToast('请先勾选商品')
    return
  }
  await showConfirmDialog({ title: '提示', message: '确定删除选中的商品吗？' })
  await removeCartItems(target)
  rows.value = rows.value.filter((r) => !target.includes(r.skuId))
  showToast('已删除')
}

/** 去结算：校验存在有效勾选商品 */
function goCheckout() {
  if (!checkedRows.value.length) {
    showToast('请先勾选要结算的商品')
    return
  }
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <div class="cart-page">
    <van-nav-bar title="购物车" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
      <template #right>
        <span class="nav-link" @click="onRemove()">删除勾选</span>
      </template>
    </van-nav-bar>

    <van-empty v-if="!loading && !rows.length" description="购物车还是空的" class="empty">
      <van-button round type="primary" to="/product/list">去逛逛</van-button>
    </van-empty>

    <template v-else>
      <van-checkbox-group :model-value="validRows.filter((r) => r.checked).map((r) => r.skuId)">
        <van-swipe-cell v-for="row in rows" :key="row.skuId">
          <div class="cart-row" :class="{ invalid: row.invalid }">
            <van-checkbox
              :model-value="row.checked"
              :disabled="!!row.invalid"
              @click="toggleChecked(row)"
            />
            <img class="thumb" :src="row.pic" @click="router.push(`/product/${row.spuId}`)" />
            <div class="info" @click="router.push(`/product/${row.spuId}`)">
              <p class="name">{{ row.spuName }}</p>
              <p v-if="row.spec" class="spec">{{ row.spec }}</p>
              <p v-if="row.invalid" class="invalid-tip">商品已失效，请删除</p>
              <p class="price">¥{{ (row.price || 0).toFixed(2) }}</p>
            </div>
            <van-stepper
              v-if="!row.invalid"
              :model-value="row.quantity"
              :max="row.stock"
              min="1"
              @change="(v: number) => { row.quantity = v; onQuantityChange(row) }"
            />
          </div>
          <template #right>
            <van-button square type="danger" text="删除" class="swipe-btn" @click="onRemove(row)" />
          </template>
        </van-swipe-cell>
      </van-checkbox-group>

      <div class="cart-footer">
        <van-checkbox :model-value="allChecked" @click="toggleAll">全选</van-checkbox>
        <div class="total">
          <span>合计：</span>
          <span class="amount">¥{{ totalAmount.toFixed(2) }}</span>
        </div>
        <van-button type="danger" round class="settle-btn" @click="goCheckout">
          去结算({{ checkedRows.length }})
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart-page {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 70px;
}
.empty {
  margin-top: 80px;
}
.cart-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f2f3f5;
}
.cart-row.invalid {
  opacity: 0.6;
}
.thumb {
  width: 72px;
  height: 72px;
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
  margin-top: 2px;
}
.invalid-tip {
  font-size: 12px;
  color: #ee0a24;
  margin-top: 2px;
}
.price {
  font-size: 15px;
  color: #ee0a24;
  font-weight: 600;
  margin-top: 4px;
}
.swipe-btn {
  height: 100%;
}
.cart-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
.total {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: #969799;
}
.amount {
  font-size: 18px;
  color: #ee0a24;
  font-weight: 600;
}
.settle-btn {
  min-width: 110px;
}
</style>
