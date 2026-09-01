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
    <div class="page-head">
      <h2>购物车</h2>
      <span class="head-count">共 {{ validRows.length }} 件商品</span>
    </div>

    <van-empty v-if="!loading && !rows.length" description="购物车还是空的" class="empty">
      <van-button round type="primary" to="/product/list">去逛逛</van-button>
    </van-empty>

    <template v-else>
      <div class="cart-table">
        <!-- 表头 -->
        <div class="cart-head">
          <div class="col-check">
            <van-checkbox :model-value="allChecked" @click="toggleAll">全选</van-checkbox>
          </div>
          <div class="col-goods">商品</div>
          <div class="col-price">单价</div>
          <div class="col-qty">数量</div>
          <div class="col-subtotal">小计</div>
          <div class="col-op">操作</div>
        </div>

        <!-- 行 -->
        <div v-for="row in rows" :key="row.skuId" class="cart-row" :class="{ invalid: row.invalid }">
          <div class="col-check">
            <van-checkbox
              :model-value="row.checked"
              :disabled="!!row.invalid"
              @click="toggleChecked(row)"
            />
          </div>
          <div class="col-goods">
            <img class="thumb" :src="row.pic" @click="router.push(`/product/${row.spuId}`)" />
            <div class="goods-info" @click="router.push(`/product/${row.spuId}`)">
              <p class="name">{{ row.spuName }}</p>
              <p v-if="row.spec" class="spec">{{ row.spec }}</p>
              <p v-if="row.invalid" class="invalid-tip">商品已失效，请删除</p>
            </div>
          </div>
          <div class="col-price">
            <span class="price">¥{{ (row.price || 0).toFixed(2) }}</span>
          </div>
          <div class="col-qty">
            <van-stepper
              v-if="!row.invalid"
              :model-value="row.quantity"
              :max="row.stock"
              min="1"
              @change="(v: number) => { row.quantity = v; onQuantityChange(row) }"
            />
            <span v-else class="invalid-mark">-</span>
          </div>
          <div class="col-subtotal">
            <span class="subtotal">¥{{ (row.subtotal || 0).toFixed(2) }}</span>
          </div>
          <div class="col-op">
            <van-button size="small" plain type="danger" @click="onRemove(row)">删除</van-button>
          </div>
        </div>
      </div>

      <!-- 底部结算条 -->
      <div class="cart-footer">
        <div class="footer-inner">
          <van-checkbox :model-value="allChecked" @click="toggleAll">全选</van-checkbox>
          <div class="footer-op">
            <van-button size="small" plain type="danger" class="remove-btn" @click="onRemove()">删除勾选</van-button>
          </div>
          <div class="total">
            <span class="total-label">合计：</span>
            <span class="amount">¥{{ totalAmount.toFixed(2) }}</span>
          </div>
          <van-button type="danger" round class="settle-btn" @click="goCheckout">
            去结算({{ checkedRows.length }})
          </van-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cart-page {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 16px;
  padding-bottom: 96px;
}
.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}
.page-head h2 {
  margin: 0;
  font-size: 22px;
  color: #323233;
  border-left: 4px solid #ee0a24;
  padding-left: 10px;
}
.head-count {
  color: #969799;
  font-size: 13px;
}
.empty {
  margin-top: 60px;
}
.cart-table {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}
.cart-head,
.cart-row {
  display: grid;
  grid-template-columns: 180px 1fr 140px 150px 140px 100px;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
}
.cart-head {
  background: #fafafa;
  color: #969799;
  font-size: 13px;
  border-bottom: 1px solid #f2f3f5;
}
.cart-row {
  border-bottom: 1px solid #f2f3f5;
}
.cart-row:last-child {
  border-bottom: none;
}
.cart-row.invalid {
  opacity: 0.6;
}
.col-check {
  display: flex;
  align-items: center;
}
.col-goods {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.thumb {
  width: 84px;
  height: 84px;
  border-radius: 8px;
  background: #f2f3f5;
  object-fit: cover;
  cursor: pointer;
  flex-shrink: 0;
}
.goods-info {
  min-width: 0;
  cursor: pointer;
}
.name {
  margin: 0;
  font-size: 15px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.name:hover {
  color: #ee0a24;
}
.spec {
  margin: 6px 0 0;
  font-size: 12px;
  color: #969799;
}
.invalid-tip {
  margin: 6px 0 0;
  font-size: 12px;
  color: #ee0a24;
}
.col-price .price {
  font-size: 15px;
  color: #323233;
}
.col-qty .invalid-mark {
  color: #969799;
}
.col-subtotal .subtotal {
  font-size: 15px;
  color: #ee0a24;
  font-weight: 600;
}
.cart-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.06);
  z-index: 50;
}
.footer-inner {
  width: min(92vw, 1680px);
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
}
.footer-op {
  flex: 1;
}
.total {
  font-size: 14px;
  color: #646566;
}
.amount {
  font-size: 22px;
  color: #ee0a24;
  font-weight: 700;
}
.settle-btn {
  min-width: 140px;
}
</style>
