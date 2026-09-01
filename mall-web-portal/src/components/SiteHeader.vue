<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCartCount } from '@/api/cart'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')
const cartCount = ref(0)

/** 搜索：回车/点击跳搜索结果页（query 透传，SearchView 读取） */
function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    showToast('请输入搜索关键词')
    return
  }
  router.push({ path: '/search', query: { keyword: kw } })
  keyword.value = ''
}

/** 购物车角标（登录后拉取件数） */
async function loadCartCount() {
  if (!userStore.isLoggedIn) {
    cartCount.value = 0
    return
  }
  try {
    cartCount.value = await getCartCount()
  } catch {
    cartCount.value = 0
  }
}

async function onLogout() {
  await userStore.logout()
  showToast('已退出登录')
  router.push('/')
}

onMounted(loadCartCount)
// 路由变化（含加购后返回）时刷新角标
watch(() => route.path, loadCartCount)
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <div class="logo" @click="router.push('/')">
        <span class="logo-badge">mall</span>
        <span class="logo-text">mall-practice 商城</span>
      </div>

      <div class="search-box">
        <van-search
          v-model="keyword"
          placeholder="搜索商品（支持分类 / 品牌关键词）"
          shape="round"
          @search="onSearch"
        />
      </div>

      <nav class="nav">
        <router-link to="/" class="nav-item" :class="{ active: route.path === '/' }">首页</router-link>
        <router-link to="/product/list" class="nav-item" :class="{ active: route.path.startsWith('/product') }">全部商品</router-link>
        <router-link to="/seckill" class="nav-item" :class="{ active: route.path === '/seckill' }">限时秒杀</router-link>
        <router-link to="/coupon-center" class="nav-item" :class="{ active: route.path === '/coupon-center' }">领券中心</router-link>
        <router-link to="/rank" class="nav-item" :class="{ active: route.path === '/rank' }">排行榜</router-link>
      </nav>

      <div class="cart" @click="router.push('/cart')">
        <van-badge :content="cartCount || undefined" :show-zero="false" :max="99">
          <van-icon name="cart-o" size="22" color="#333" />
        </van-badge>
        <span class="cart-text">购物车</span>
      </div>

      <div v-if="userStore.isLoggedIn" class="user">
        <span class="nickname" @click="router.push('/profile')">{{ userStore.nickname }}</span>
        <van-button size="small" plain type="primary" class="logout-btn" @click="onLogout">退出</van-button>
      </div>
      <div v-else class="user">
        <router-link to="/login" class="login-link">登录</router-link>
        <van-button size="small" type="primary" round @click="router.push('/register')">注册</van-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 10px 16px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
}
.logo-badge {
  background: #ee0a24;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
}
.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #323233;
  white-space: nowrap;
}
.search-box {
  flex: 1;
  min-width: 200px;
  max-width: 420px;
}
.nav {
  display: flex;
  gap: 20px;
  white-space: nowrap;
}
.nav-item {
  color: #555;
  font-size: 15px;
  text-decoration: none;
  padding: 6px 2px;
  border-bottom: 2px solid transparent;
  transition: color 0.2s;
}
.nav-item:hover {
  color: #ee0a24;
}
.nav-item.active {
  color: #ee0a24;
  border-bottom-color: #ee0a24;
  font-weight: 600;
}
.cart {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.cart-text {
  font-size: 14px;
  color: #333;
}
.cart:hover .cart-text {
  color: #ee0a24;
}
.user {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.nickname {
  font-size: 14px;
  color: #333;
  cursor: pointer;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nickname:hover {
  color: #ee0a24;
}
.logout-btn {
  height: 28px;
  line-height: 26px;
}
.login-link {
  font-size: 14px;
  color: #333;
  text-decoration: none;
}
.login-link:hover {
  color: #ee0a24;
}
</style>
