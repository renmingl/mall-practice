import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { vPerm } from './directives/perm'
import './styles/index.css'

// Mock 演示模式（VITE_MOCK=true，详见 README「Mock 演示模式」）：无登录态时注入演示 token，
// 便于无需后端直接浏览业务页面（演示数据由 vite mock 中间件提供，路由守卫 /me 恢复超级管理员身份）；
// URL 带 mockNoLogin=1 时跳过注入，用于演示未登录入口页（如登录页）
if (import.meta.env.VITE_MOCK === 'true' && !location.search.includes('mockNoLogin=1')) {
  if (!localStorage.getItem('admin_access_token')) {
    localStorage.setItem('admin_access_token', 'mock-admin-token')
    localStorage.setItem('admin_refresh_token', 'mock-admin-refresh')
  }
}

// 管理后台入口：Pinia + Router + Element Plus（全量引入；图标全局注册供动态菜单 <component :is> 使用）
const app = createApp(App)

// 注册全部图标（菜单树 icon 字段为字符串名，如 Setting/User/Menu）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.directive('perm', vPerm)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
