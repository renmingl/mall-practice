import { defineStore } from 'pinia'
import {
  adminLogin,
  adminLogout,
  getAdminMe,
  type AdminLoginResult
} from '@/api/auth'

// 登录态持久化 key（localStorage：刷新页面恢复登录态）
const TOKEN_KEY = 'admin_access_token'
const REFRESH_KEY = 'admin_refresh_token'

interface UserState {
  accessToken: string
  refreshToken: string
  user: AdminLoginResult['user'] | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    user: null
  }),
  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    /** 是否超级管理员（权限标识 *） */
    isSuperAdmin: (state) => state.user?.perms.includes('*') ?? false,
    nickname: (state) => state.user?.nickname || state.user?.username || '管理员'
  },
  actions: {
    setLogin(result: AdminLoginResult) {
      this.accessToken = result.accessToken
      this.refreshToken = result.refreshToken
      this.user = result.user
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      localStorage.setItem(REFRESH_KEY, result.refreshToken)
    },

    /** 登录（页面调用） */
    async login(payload: { username: string; password: string; captchaUuid: string; captchaCode: string }) {
      const result = await adminLogin(payload)
      this.setLogin(result)
      return result
    },

    /** 恢复登录态：刷新页面后调 /me 拉取角色/权限 */
    async fetchMe() {
      const me = await getAdminMe()
      this.user = { ...this.user, ...me, userType: 'ADMIN' } as AdminLoginResult['user']
      return this.user
    },

    /** 退出：调后端黑名单 + 清本地 */
    async logout() {
      try {
        await adminLogout()
      } finally {
        this.clear()
      }
    },

    /** 清空登录态（退出/401） */
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
    }
  }
})
