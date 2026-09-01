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
    // 阶段 8：ES 商品搜索（联想/高亮，公开）
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('@/views/FavoriteListView.vue'),
      meta: { requiresAuth: true }
    },
    // 阶段 4：购物车与优惠券（需登录）
    {
      path: '/cart',
      name: 'cart',
      component: () => import('@/views/CartView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/coupon-center',
      name: 'coupon-center',
      component: () => import('@/views/CouponCenterView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/my-coupons',
      name: 'my-coupons',
      component: () => import('@/views/MyCouponView.vue'),
      meta: { requiresAuth: true }
    },
    // 阶段 5：结算与订单（需登录）
    {
      path: '/checkout',
      name: 'checkout',
      component: () => import('@/views/CheckoutView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/OrderListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/order/:orderSn',
      name: 'order-detail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/cashier',
      name: 'cashier',
      component: () => import('@/views/CashierView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/pay-result',
      name: 'pay-result',
      component: () => import('@/views/PayResultView.vue'),
      meta: { requiresAuth: true }
    },
    // 阶段 6：退款与评价（需登录）
    {
      path: '/refunds',
      name: 'refunds',
      component: () => import('@/views/RefundListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/refund/apply',
      name: 'refund-apply',
      component: () => import('@/views/RefundApplyView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/comment',
      name: 'comment',
      component: () => import('@/views/CommentView.vue'),
      meta: { requiresAuth: true }
    },
    // 阶段 7：秒杀与运营（会场/排行榜公开，签到/足迹需登录）
    { path: '/seckill', name: 'seckill', component: () => import('@/views/SeckillView.vue') },
    { path: '/rank', name: 'rank', component: () => import('@/views/RankView.vue') },
    {
      path: '/checkin',
      name: 'checkin',
      component: () => import('@/views/CheckinView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/HistoryView.vue'),
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
