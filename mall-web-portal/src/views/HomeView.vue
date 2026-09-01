<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getBanners, getCategories, getHotProducts, type Banner, type CategoryNode, type Spu } from '@/api/product'

const router = useRouter()

const banners = ref<Banner[]>([])
const hotProducts = ref<Spu[]>([])
/** 顶层分类（快捷入口） */
const topCategories = ref<CategoryNode[]>([])

// ---------- Banner 轮播（自实现：纯 CSS 百分比宽度，不依赖 JS 测量，headless 截图/低端环境均稳定） ----------
const activeIndex = ref(0)
let autoTimer: ReturnType<typeof setInterval> | null = null
const bannerCount = computed(() => banners.value.length)

function startAuto() {
  stopAuto()
  if (bannerCount.value < 2) return
  autoTimer = setInterval(() => {
    activeIndex.value = (activeIndex.value + 1) % bannerCount.value
  }, 3500)
}

function stopAuto() {
  if (autoTimer) {
    clearInterval(autoTimer)
    autoTimer = null
  }
}

function goBanner(b: Banner) {
  router.push(b.link)
}

async function load() {
  try {
    banners.value = await getBanners()
  } catch {
    banners.value = []
  }
  startAuto()
  try {
    hotProducts.value = await getHotProducts(8)
  } catch {
    hotProducts.value = []
    showToast('热销商品加载失败')
  }
  try {
    topCategories.value = (await getCategories()).filter((c) => !c.parentId)
  } catch {
    topCategories.value = []
  }
}

function goDetail(spu: Spu) {
  router.push(`/product/${spu.id}`)
}

/** 分类快捷入口点击 → 商品列表按分类筛选 */
function goCategory(c: CategoryNode) {
  router.push({ path: '/product/list', query: { categoryId: String(c.id) } })
}

onMounted(load)
onBeforeUnmount(stopAuto)
</script>

<template>
  <div class="home">
    <!-- 首页运营 Banner 轮播（自实现，宽度纯 CSS 百分比） -->
    <section
      class="banner-wrap"
      @mouseenter="stopAuto"
      @mouseleave="startAuto"
    >
      <div
        v-if="banners.length"
        class="banner-swipe"
      >
        <div
          class="banner-track"
          :style="{ transform: `translateX(-${activeIndex * 100}%)` }"
        >
          <div
            v-for="b in banners"
            :key="b.id"
            class="banner-item"
            :style="{ background: `linear-gradient(120deg, ${b.color1}, ${b.color2})` }"
            @click="goBanner(b)"
          >
            <div class="banner-text">
              <h2>{{ b.title }}</h2>
              <p>{{ b.subtitle }}</p>
            </div>
            <span class="banner-emoji">{{ b.emoji }}</span>
          </div>
        </div>
        <div v-if="banners.length > 1" class="banner-dots">
          <i
            v-for="(b, i) in banners"
            :key="'dot-' + b.id"
            :class="{ active: i === activeIndex }"
            @click="activeIndex = i"
          ></i>
        </div>
      </div>
    </section>

    <!-- 分类快捷入口 -->
    <section class="section">
      <div class="cat-grid">
        <div v-for="c in topCategories" :key="c.id" class="cat-item" @click="goCategory(c)">
          <van-icon :name="c.icon || 'apps-o'" class="cat-icon" />
          <span class="cat-name">{{ c.name }}</span>
        </div>
        <div class="cat-item" @click="router.push('/product/list')">
          <van-icon name="apps-o" class="cat-icon" />
          <span class="cat-name">全部商品</span>
        </div>
      </div>
    </section>

    <!-- 限时秒杀入口横幅 -->
    <section class="section">
      <div class="seckill-banner" @click="router.push('/seckill')">
        <van-icon name="flash" class="seckill-icon" />
        <div class="seckill-text">
          <h3>限时秒杀</h3>
          <p>整点开抢 · 全场低至 5 折</p>
        </div>
        <van-button size="small" round type="danger" class="seckill-btn">去抢购</van-button>
      </div>
    </section>

    <!-- 热销商品 -->
    <section class="section">
      <div class="section-head">
        <h3 class="section-title">热销商品</h3>
        <span class="more" @click="router.push('/product/list')">查看更多 ›</span>
      </div>
      <div class="product-grid">
        <div v-for="spu in hotProducts" :key="spu.id" class="p-card" @click="goDetail(spu)">
          <img :src="spu.mainPic" :alt="spu.name" class="p-img" />
          <div class="p-info">
            <p class="p-name" :title="spu.name">{{ spu.name }}</p>
            <p class="p-sub" :title="spu.subtitle">{{ spu.subtitle }}</p>
            <div class="p-bottom">
              <span class="p-price">¥{{ (spu.price ?? 0).toFixed(2) }}</span>
              <span class="p-sales">已售 {{ spu.sales ?? 0 }} 件</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 16px;
}
.banner-wrap {
  border-radius: 12px;
  overflow: hidden;
}
.banner-swipe {
  position: relative;
  aspect-ratio: 4.6 / 1;
}
.banner-track {
  display: flex;
  height: 100%;
  transition: transform 0.5s ease;
}
.banner-item {
  flex: 0 0 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 64px;
  cursor: pointer;
}
.banner-dots {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
}
.banner-dots i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: background 0.2s, width 0.2s;
}
.banner-dots i.active {
  width: 20px;
  border-radius: 4px;
  background: #fff;
}
.banner-text h2 {
  margin: 0;
  color: #fff;
  font-size: 40px;
  letter-spacing: 2px;
}
.banner-text p {
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.92);
  font-size: 18px;
}
.banner-emoji {
  font-size: 120px;
  filter: drop-shadow(0 8px 16px rgba(0, 0, 0, 0.25));
  user-select: none;
}
.section {
  margin-top: 20px;
}
.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.cat-item {
  background: #fff;
  border-radius: 10px;
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.cat-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}
.cat-icon {
  font-size: 34px;
  color: #ee0a24;
}
.cat-name {
  font-size: 15px;
  color: #323233;
}
.seckill-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(120deg, #f43f5e, #ee0a24);
  border-radius: 12px;
  padding: 18px 28px;
  cursor: pointer;
  color: #fff;
}
.seckill-icon {
  font-size: 36px;
}
.seckill-text {
  flex: 1;
}
.seckill-text h3 {
  margin: 0;
  font-size: 20px;
}
.seckill-text p {
  margin: 4px 0 0;
  font-size: 13px;
  opacity: 0.9;
}
.seckill-btn {
  background: #fff;
  border-color: #fff;
  color: #ee0a24;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-title {
  margin: 0;
  font-size: 20px;
  color: #323233;
  border-left: 4px solid #ee0a24;
  padding-left: 10px;
}
.more {
  color: #969799;
  font-size: 14px;
  cursor: pointer;
}
.more:hover {
  color: #ee0a24;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.p-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.p-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-3px);
}
.p-img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  background: #f2f3f5;
  display: block;
}
.p-info {
  padding: 12px 14px 14px;
}
.p-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
}
.p-price {
  color: #ee0a24;
  font-size: 18px;
  font-weight: 700;
}
.p-sales {
  color: #969799;
  font-size: 12px;
}
</style>
