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
import { getLikeCount, getLikeStatus, likeSpu, trackView, unlikeSpu } from '@/api/seckill'
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

// 运营数据：点赞（10.5）+ 浏览埋点（10.2）
const liked = ref(false)
const likeCount = ref(0)
const likeLoading = ref(false)

/** 主图切换（默认主图，缩略图可切换） */
const pics = computed(() =>
  detail.value ? [detail.value.spu.mainPic, ...(detail.value.spu.pics ? detail.value.spu.pics.split(',') : [])].filter(Boolean) as string[] : []
)
const activePic = ref('')

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
    activePic.value = detail.value.spu.mainPic || ''
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

/** 点赞状态（未登录不查询，仅展示公开计数） */
async function loadLikeStatus() {
  try {
    likeCount.value = await getLikeCount(spuId)
  } catch {
    likeCount.value = 0
  }
  if (!userStore.isLoggedIn) return
  try {
    liked.value = await getLikeStatus(spuId)
  } catch {
    liked.value = false
  }
}

/** 点赞切换（需登录；Set 天然幂等） */
async function toggleLike() {
  if (!userStore.isLoggedIn) {
    showToast('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  likeLoading.value = true
  try {
    if (liked.value) {
      await unlikeSpu(spuId)
      liked.value = false
      likeCount.value = Math.max(0, likeCount.value - 1)
    } else {
      await likeSpu(spuId)
      liked.value = true
      likeCount.value += 1
    }
  } finally {
    likeLoading.value = false
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
  // 浏览埋点（PV + UV + 浏览排行 + 足迹），失败不影响详情页
  trackView(spuId).catch(() => {})
  loadLikeStatus()
})
</script>

<template>
  <div class="product-detail">
    <div v-if="detail" class="detail-wrap">
      <!-- 左：主图 + 缩略图 -->
      <div class="gallery">
        <div class="main-img">
          <img :src="activePic || detail.spu.mainPic" :alt="detail.spu.name" />
        </div>
        <div v-if="pics.length > 1" class="thumbs">
          <div
            v-for="(pic, i) in pics"
            :key="i"
            class="thumb"
            :class="{ active: pic === activePic }"
            @click="activePic = pic"
          >
            <img :src="pic" :alt="detail.spu.name + (i + 1)" />
          </div>
        </div>
      </div>

      <!-- 右：信息 + 操作 -->
      <div class="info">
        <h1 class="name">{{ detail.spu.name }}</h1>
        <p v-if="detail.spu.subtitle" class="subtitle">{{ detail.spu.subtitle }}</p>

        <div class="price-box">
          <span class="price">¥{{ price.toFixed(2) }}</span>
          <span class="sales">已售 {{ detail.spu.sales ?? 0 }} 件 · 库存 {{ stock }} 件</span>
        </div>

        <p class="meta">{{ detail.categoryName }} · {{ detail.brandName }} · {{ detail.spu.unit }}</p>

        <!-- SKU 选择 -->
        <div class="sku-box">
          <p class="sku-title">选择规格</p>
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
        </div>

        <!-- 操作区 -->
        <div class="actions">
          <van-button type="warning" size="large" round class="act-btn" @click="onAddToCart">加入购物车</van-button>
          <van-button type="danger" size="large" round class="act-btn" @click="onBuyNow">立即购买</van-button>
          <van-button
            size="large"
            round
            class="act-btn"
            :icon="favorited ? 'star' : 'star-o'"
            :loading="favoriteLoading"
            @click="toggleFavorite"
          >
            {{ favorited ? '已收藏' : '收藏' }}
          </van-button>
          <van-button
            size="large"
            round
            class="act-btn"
            :icon="liked ? 'good-job' : 'good-job-o'"
            :loading="likeLoading"
            @click="toggleLike"
          >
            点赞 {{ likeCount }}
          </van-button>
        </div>
      </div>
    </div>

    <!-- 商品详情 -->
    <div v-if="detail" class="detail-box">
      <h3 class="detail-title">商品详情</h3>
      <div class="detail-text">{{ detail.spu.detail || '暂无详情' }}</div>
    </div>

    <van-loading v-if="!detail" class="loading" size="32" vertical>加载中...</van-loading>
  </div>
</template>

<style scoped>
.product-detail {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 16px;
}
.detail-wrap {
  display: flex;
  gap: 32px;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
}
.gallery {
  width: min(400px, 40vw);
  flex-shrink: 0;
}
.main-img {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 10px;
  overflow: hidden;
  background: #f2f3f5;
}
.main-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumbs {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  cursor: pointer;
  background: #f2f3f5;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb.active {
  border-color: #ee0a24;
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  margin: 0;
  font-size: 24px;
  line-height: 1.4;
  color: #323233;
}
.subtitle {
  margin: 10px 0 0;
  color: #969799;
  font-size: 14px;
}
.price-box {
  display: flex;
  align-items: baseline;
  gap: 16px;
  background: #fff1f0;
  border-radius: 8px;
  padding: 14px 16px;
  margin-top: 18px;
}
.price {
  color: #ee0a24;
  font-size: 30px;
  font-weight: 700;
}
.sales {
  color: #969799;
  font-size: 13px;
}
.meta {
  margin: 14px 0 0;
  color: #969799;
  font-size: 13px;
}
.sku-box {
  margin-top: 18px;
}
.sku-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #646566;
  font-weight: 600;
}
.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.sku-tag {
  padding: 8px 14px;
  cursor: pointer;
}
.out-of-stock {
  opacity: 0.6;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}
.act-btn {
  min-width: 132px;
}
.detail-box {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-top: 16px;
}
.detail-title {
  margin: 0 0 12px;
  font-size: 16px;
  color: #323233;
  border-left: 4px solid #ee0a24;
  padding-left: 10px;
}
.detail-text {
  color: #555;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}
.loading {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}
</style>
