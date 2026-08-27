<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { ping, trace } from '@/api/ping'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const result = ref('')
const loading = ref(false)

const nickname = computed(() => userStore.nickname)

// 骨架验证：/api/common/ping + /api/common/trace，验证网关路由与 traceId 全链路透传
async function verify() {
  loading.value = true
  try {
    result.value = await ping()
    appStore.setTraceId(await trace())
    showToast('网关链路验证通过')
  } catch {
    showToast('网关链路验证失败：请确认 gateway(8080) 与 auth(8100) 已启动')
  } finally {
    loading.value = false
  }
}

onMounted(verify)
</script>

<template>
  <div class="home">
    <van-nav-bar title="mall-practice 商城">
      <template #right>
        <template v-if="userStore.isLoggedIn">
          <span class="nav-link" @click="router.push('/profile')">{{ nickname }}</span>
        </template>
        <template v-else>
          <span class="nav-link" @click="router.push('/login')">登录</span>
          <span class="nav-link" @click="router.push('/register')">注册</span>
        </template>
      </template>
    </van-nav-bar>

    <h1 class="title">mall-web-portal 前台商城</h1>
    <van-card class="card">
      <template #title>骨架链路验证</template>
      <template #desc>
        <p>后端验证接口：<code>/api/common/ping</code>、<code>/api/common/trace</code>（经网关 → mall-auth）</p>
        <van-button type="primary" size="small" :loading="loading" @click="verify">重新验证</van-button>
        <p class="row">ping 响应：<code>{{ result || '未请求' }}</code></p>
        <p class="row">traceId（请求头 X-Trace-Id）：<code>{{ appStore.traceId || '未获取' }}</code></p>
      </template>
    </van-card>

    <!-- 阶段 3：商品中心入口 -->
    <van-cell-group inset title="商品中心">
      <van-cell title="商品列表" is-link to="/product/list" icon="shopping-cart-o" />
      <van-cell v-if="userStore.isLoggedIn" title="我的收藏" is-link to="/favorites" icon="star-o" />
    </van-cell-group>

    <!-- 登录后功能区 -->
    <van-cell-group v-if="userStore.isLoggedIn" inset title="个人中心">
      <van-cell title="我的资料" is-link to="/profile" icon="user-o" />
      <van-cell title="收货地址" is-link to="/address" icon="location-o" />
    </van-cell-group>
  </div>
</template>

<style scoped>
.home {
  max-width: 640px;
  margin: 0 auto;
}
.title {
  text-align: center;
  margin: 24px 0;
}
.nav-link {
  margin-left: 16px;
  color: #1989fa;
  cursor: pointer;
}
.row {
  margin-top: 12px;
  word-break: break-all;
}
</style>
