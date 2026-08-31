<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import {
  addFavorite,
  getFavoriteStatus,
  getProductDetail,
  removeFavorite,
  type ProductDetail,
  type Sku
} from '@/api/product'
import { addToCart } from '@/api/cart'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const spuId = Number(route.params.id)

const loading = ref(false)
const detail = ref<ProductDetail | null>(null)
const activeSku = ref<Sku | null>(null)
const favorited = ref(false)
const favoriteLoading = ref(false)

/** 默认选中第一个启用 SKU */
const price = computed(() => activeSku.value?.price ?? detail.value?.skuList?.[0]?.price ?? 0)
const stock = computed(() => activeSku.value?.stock ?? detail.value?.skuList?.[0]?.stock ?? 0)

function selectSku(sku: Sku) {
  activeSku.value = sku
}

async function load() {
  loading.value = true
  try {
    detail.value = await getProductDetail(spuId)
    activeSku.value = detail.value.skuList[0] || null
  } finally {
    loading.value = false
  }
}

/** 收藏切换（需登录；未登录跳登录页并回跳） */
async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    showToast('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  favoriteLoading.value = true
  try {
    if (favorited.value) {
      await removeFavorite(spuId)
      favorited.value = false
      showToast('已取消收藏')
    } else {
      await addFavorite(spuId)
      favorited.value = true
      showToast('收藏成功')
    }
  } finally {
    favoriteLoading.value = false
  }
}

async function loadFavoriteStatus() {
  if (!userStore.isLoggedIn) return
  try {
    favorited.value = await getFavoriteStatus(spuId)
  } catch {
    favorited.value = false
  }
}

/** 阶段 4：加入购物车（需登录；加购默认勾选） */
async function onAddToCart() {
  if (!userStore.isLoggedIn) {
    showToast('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!activeSku.value) {
    showToast('请先选择规格')
    return
  }
  await addToCart(activeSku.value.id, 1)
  showToast('已加入购物车')
}

/** 立即购买：加购（默认勾选）后直达结算页 */
async function onBuyNow() {
  if (!userStore.isLoggedIn) {
    showToast('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!activeSku.value) {
    showToast('请先选择规格')
    return
  }
  await addToCart(activeSku.value.id, 1)
  router.push('/checkout')
}

onMounted(() => {
  load()
  loadFavoriteStatus()
})
</script>

<template>
  <div class="product-detail">
    <van-nav-bar title="商品详情" fixed placeholder @click-left="router.back()">
      <template #left>
        <van-icon name="arrow-left" />
      </template>
    </van-nav-bar>

    <div v-if="detail" class="detail-body">
      <!-- 主图轮播 -->
      <van-swipe :autoplay="0" class="swipe">
        <van-swipe-item v-for="(pic, i) in [detail.spu.mainPic, ...(detail.spu.pics ? detail.spu.pics.split(',') : [])].filter(Boolean)" :key="i">
          <img :src="pic" class="swipe-img" />
        </van-swipe-item>
      </van-swipe>

      <!-- 价格/标题 -->
      <div class="price-bar">
        <span class="price">¥{{ price.toFixed(2) }}</span>
        <span class="sales">已售 {{ detail.spu.sales ?? 0 }} 件</span>
      </div>
      <div class="title-bar">
        <h2 class="name">{{ detail.spu.name }}</h2>
        <p v-if="detail.spu.subtitle" class="subtitle">{{ detail.spu.subtitle }}</p>
        <p class="meta">
          {{ detail.categoryName }} · {{ detail.brandName }} · {{ detail.spu.unit }}
        </p>
      </div>

      <!-- SKU 选择 -->
      <van-cell-group inset title="选择规格">
        <div class="sku-list">
          <van-tag
            v-for="sku in detail.skuList"
            :key="sku.id"
            :type="activeSku?.id === sku.id ? 'primary' : 'default'"
            size="large"
            class="sku-tag"
            @click="selectSku(sku)"
          >
            {{ sku.spec || sku.skuCode }}
            <span v-if="sku.stock <= 0" class="out-of-stock">（缺货）</span>
          </van-tag>
        </div>
        <p class="stock-tip">库存：{{ stock }} 件</p>
      </van-cell-group>

      <!-- 详情 -->
      <van-cell-group inset title="商品详情">
        <div class="detail-text">{{ detail.spu.detail || '暂无详情' }}</div>
      </van-cell-group>
    </div>

    <!-- 底部操作栏 -->
    <van-action-bar safe-area-inset-bottom>
      <van-action-bar-icon :icon="favorited ? 'star' : 'star-o'" :color="favorited ? '#ee0a24' : '#646566'" :loading="favoriteLoading" text="收藏" @click="toggleFavorite" />
      <van-action-bar-button type="warning" text="加入购物车" @click="onAddToCart" />
      <van-action-bar-button type="danger" text="立即购买" @click="onBuyNow" />
    </van-action-bar>
  </div>
</template>

<style scoped>
.product-detail {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 60px;
}
.swipe {
  height: 300px;
  background: #f7f8fa;
}
.swipe-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.price-bar {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 12px 16px 0;
}
.price {
  color: #ee0a24;
  font-size: 22px;
  font-weight: 600;
}
.sales {
  color: #969799;
  font-size: 12px;
}
.title-bar {
  padding: 8px 16px 16px;
}
.name {
  margin: 0;
  font-size: 17px;
  line-height: 1.4;
}
.subtitle {
  margin: 6px 0 0;
  color: #969799;
  font-size: 13px;
}
.meta {
  margin: 6px 0 0;
  color: #969799;
  font-size: 12px;
}
.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 16px 0;
}
.sku-tag {
  padding: 6px 10px;
}
.out-of-stock {
  opacity: 0.6;
}
.stock-tip {
  margin: 8px 16px 12px;
  color: #969799;
  font-size: 12px;
}
.detail-text {
  padding: 8px 16px;
  color: #333;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
