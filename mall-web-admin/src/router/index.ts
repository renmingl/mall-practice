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
        { path: 'menu', name: 'menu', component: () => import('@/views/menu/MenuView.vue'), meta: { title: '菜单管理' } }
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
