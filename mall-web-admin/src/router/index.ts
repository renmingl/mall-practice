import { createRouter, createWebHistory } from 'vue-router'

// 阶段 0 脚手架路由：仅骨架验证首页 + 404；业务页面随各阶段添加
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

export default router
