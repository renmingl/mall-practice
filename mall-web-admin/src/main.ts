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

// 管理后台入口：Pinia + Router + Element Plus（全量引入；图标全局注册供动态菜单 <component :is> 使用）
const app = createApp(App)

// 注册全部图标（菜单树 icon 字段为字符串名，如 Setting/User/Menu）
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.directive('perm', vPerm)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
