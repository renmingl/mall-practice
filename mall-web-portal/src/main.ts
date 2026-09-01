import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// Mock 演示模式（VITE_MOCK=true，详见 README「Mock 演示模式」）：无登录态时注入演示 token，
// 便于无需后端直接浏览需登录的页面（演示数据由 vite mock 中间件提供）；
// URL 带 mockNoLogin=1 时跳过注入，用于演示未登录入口页（如登录页）
if (import.meta.env.VITE_MOCK === 'true' && !location.search.includes('mockNoLogin=1')) {
  if (!localStorage.getItem('portal_access_token')) {
    localStorage.setItem('portal_access_token', 'mock-access-token')
    localStorage.setItem('portal_refresh_token', 'mock-refresh-token')
  }
}

// 前台商城脚手架入口：Pinia + Router + Vant 全量引入（按需引入待业务页面落地时优化）
createApp(App).use(createPinia()).use(router).use(Vant).mount('#app')
