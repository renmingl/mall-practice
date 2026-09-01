<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'

const route = useRoute()

// 登录/注册/忘记密码页为独立表单页（PC 电商惯例不带站点头部/页脚）
const showChrome = computed(
  () => !['login', 'register', 'forgot-password'].includes(String(route.name))
)
</script>

<template>
  <div class="app-shell">
    <SiteHeader v-if="showChrome" />
    <main class="app-main">
      <router-view />
    </main>
    <footer v-if="showChrome" class="site-footer">
      <p>mall-practice 电商微服务学习项目 · Spring Cloud Alibaba + Vue 3 + Vant</p>
    </footer>
  </div>
</template>

<style scoped>
.app-main {
  min-height: calc(100vh - 220px);
}
.site-footer {
  border-top: 1px solid #ebedf0;
  background: #fff;
  color: #969799;
  font-size: 13px;
  text-align: center;
  padding: 20px 0;
  margin-top: 40px;
}
</style>
