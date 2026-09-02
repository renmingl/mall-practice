import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/user',
      children: [
        { path: 'user', name: 'user', component: () => import('@/views/user/UserView.vue'), meta: { title: '用户管理' } },
        { path: 'role', name: 'role', component: () => import('@/views/role/RoleView.vue'), meta: { title: '角色管理' } },
        { path: 'menu', name: 'menu', component: () => import('@/views/menu/MenuView.vue'), meta: { title: '菜单管理' } },
        // 阶段 3：商品域与进销存（路由固定注册；侧边栏菜单由 admin_menu 种子控制）
        { path: 'category', name: 'category', component: () => import('@/views/category/CategoryView.vue'), meta: { title: '分类管理' } },
        { path: 'brand', name: 'brand', component: () => import('@/views/brand/BrandView.vue'), meta: { title: '品牌管理' } },
        { path: 'product', name: 'product', component: () => import('@/views/product/ProductView.vue'), meta: { title: '商品管理' } },
        { path: 'product/edit/:id?', name: 'product-edit', component: () => import('@/views/product/ProductEditView.vue'), meta: { title: '商品编辑' } },
        { path: 'supplier', name: 'supplier', component: () => import('@/views/supplier/SupplierView.vue'), meta: { title: '供应商管理' } },
        { path: 'purchase', name: 'purchase', component: () => import('@/views/purchase/PurchaseView.vue'), meta: { title: '采购管理' } },
        { path: 'stock', name: 'stock', component: () => import('@/views/stock/StockView.vue'), meta: { title: '库存管理' } },
        // 阶段 4/5/6：营销与交易（菜单由 admin_menu 种子控制，路径与菜单 path 对齐）
        { path: 'coupon-template', name: 'coupon-template', component: () => import('@/views/coupon/CouponTemplateView.vue'), meta: { title: '优惠券模板' } },
        { path: 'order', name: 'order', component: () => import('@/views/order/OrderView.vue'), meta: { title: '订单管理' } },
        { path: 'refund', name: 'refund', component: () => import('@/views/refund/RefundView.vue'), meta: { title: '退款管理' } },
        { path: 'comment', name: 'comment', component: () => import('@/views/comment/CommentView.vue'), meta: { title: '评价管理' } },
        // 阶段 7：运营数据看板 + 秒杀配置（场次/商品同一页面按路径定位 tab；菜单由 admin_menu 种子控制）
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '数据看板' } },
        { path: 'seckill/session', name: 'seckill-session', component: () => import('@/views/seckill/SeckillAdminView.vue'), meta: { title: '秒杀场次' } },
        { path: 'seckill/product', name: 'seckill-product', component: () => import('@/views/seckill/SeckillAdminView.vue'), meta: { title: '秒杀商品' } },
        // 阶段 9：AI 助手（后台管理员对话；菜单由 admin_menu 种子控制）
        { path: 'ai', name: 'ai-chat', component: () => import('@/views/ai/AiChatView.vue'), meta: { title: 'AI 助手' } }
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

// 登录守卫：业务页需登录；已登录访问 /login 回首页；刷新后拉取 /me 恢复角色权限
router.beforeEach(async (to) => {
  const userStore = useUserStore()
  if (to.name === 'login') {
    return userStore.isLoggedIn ? { path: '/' } : true
  }
  if (!userStore.isLoggedIn) {
    return { path: '/login' }
  }
  // 已有 token 但内存无用户信息（刷新页面）：拉 /me 恢复登录态
  if (!userStore.user) {
    try {
      await userStore.fetchMe()
    } catch {
      userStore.clear()
      return { path: '/login' }
    }
  }
  return true
})

export default router
