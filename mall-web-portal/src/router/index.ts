import { createRouter, createWebHistory } from 'vue-router'

// 需要登录才能访问的路由（meta.requiresAuth），未登录跳 /login 并携带回跳地址
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue') },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue')
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/address',
      name: 'address',
      component: () => import('@/views/AddressView.vue'),
      meta: { requiresAuth: true }
    },
    // 阶段 3：商品列表/详情（公开）与我的收藏（需登录）
    { path: '/product/list', name: 'product-list', component: () => import('@/views/ProductListView.vue') },
    { path: '/product/:id', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue') },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('@/views/FavoriteListView.vue'),
      meta: { requiresAuth: true }
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

// 登录守卫：白名单（home/login/register）之外的路由需携带 access token
router.beforeEach((to) => {
  const token = localStorage.getItem('portal_access_token')
  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'register' || to.name === 'forgot-password') && token) {
    return { path: '/' }
  }
  return true
})

export default router
