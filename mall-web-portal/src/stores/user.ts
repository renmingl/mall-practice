import { defineStore } from 'pinia'
import {
  login as apiLogin,
  logout as apiLogout,
  register as apiRegister,
  type LoginResult
} from '@/api/auth'

// 登录态持久化 key（localStorage：刷新页面恢复登录态）
const TOKEN_KEY = 'portal_access_token'
const REFRESH_KEY = 'portal_refresh_token'

interface UserState {
  accessToken: string
  refreshToken: string
  user: LoginResult['user'] | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    user: null
  }),
  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    nickname: (state) => state.user?.nickname || state.user?.username || '未登录'
  },
  actions: {
    /** 登录成功落库（token + 用户信息） */
    setLogin(result: LoginResult) {
      this.accessToken = result.accessToken
      this.refreshToken = result.refreshToken
      this.user = result.user
      localStorage.setItem(TOKEN_KEY, result.accessToken)
      localStorage.setItem(REFRESH_KEY, result.refreshToken)
    },

    /** 登录（页面调用） */
    async login(payload: { username: string; password: string; captchaUuid: string; captchaCode: string }) {
      const result = await apiLogin(payload)
      this.setLogin(result)
      return result
    },

    /** 注册（注册即登录，后端直接签发双令牌） */
    async loginOrRegister(payload: {
      username: string
      password: string
      nickname?: string
      phone?: string
      email?: string
      captchaUuid: string
      captchaCode: string
    }) {
      const result = await apiRegister(payload)
      this.setLogin(result)
      return result
    },

    /** 退出：调后端黑名单 + 清本地 */
    async logout() {
      try {
        await apiLogout()
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
