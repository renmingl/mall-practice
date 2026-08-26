import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// 前台商城脚手架入口：Pinia + Router + Vant 全量引入（按需引入待业务页面落地时优化）
createApp(App).use(createPinia()).use(router).use(Vant).mount('#app')
