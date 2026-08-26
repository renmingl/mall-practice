import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// 管理后台脚手架入口：Pinia + Router + Element Plus 全量引入（按需引入待业务页面落地时优化）
createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
