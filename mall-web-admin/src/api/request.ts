import axios, { type AxiosRequestConfig, type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一返回结构（与 mall-common Result<T> 对齐） */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

// 登录态 key 与 stores/user.ts 保持一致（拦截器直接读 localStorage，避免与 store 循环依赖）
const TOKEN_KEY = 'admin_access_token'
const REFRESH_KEY = 'admin_refresh_token'

// axios 封装：baseURL 走 /api 前缀（dev 经 Vite 代理 → 网关 8080）
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

// 请求拦截：每次请求生成独立 X-Trace-Id（请求级追踪，跨请求不复用）+ Authorization（登录后自动附带）
service.interceptors.request.use((config) => {
  config.headers['X-Trace-Id'] = crypto.randomUUID().replace(/-/g, '')

  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// refresh 静默续期：并发 401 共享同一次刷新（refresh 轮换，旧 refresh 一次性使用）
let refreshPromise: Promise<boolean> | null = null

function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = localStorage.getItem(REFRESH_KEY)
      if (!refreshToken) {
        return false
      }
      try {
        // 裸 axios 调 /auth/refresh（不经本实例拦截器，避免递归）
        const resp = await axios.post(`${service.defaults.baseURL}/auth/refresh`, { refreshToken })
        const res = resp.data as ApiResult<{ accessToken: string; refreshToken: string }>
        if (res.code === 200 && res.data?.accessToken) {
          localStorage.setItem(TOKEN_KEY, res.data.accessToken)
          localStorage.setItem(REFRESH_KEY, res.data.refreshToken)
          return true
        }
        return false
      } catch {
        return false
      } finally {
        refreshPromise = null
      }
    })()
  }
  return refreshPromise
}

// 响应拦截：Result<T> 解包，非 200 统一提示；401 先静默续期重放，续期失败才清登录态跳登录页
service.interceptors.response.use(
  (response): any => {
    const res = response.data as ApiResult
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  async (error: AxiosError<ApiResult>) => {
    const status = error.response?.status
    const config = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    // 登录/刷新接口自身 401 不重试（登录失败与 refresh 失效直接走清态逻辑）
    const noRetry = config?.url?.includes('/auth/login') || config?.url?.includes('/auth/refresh')
    if (status === 401 && config && !config._retried && !noRetry) {
      if (await refreshAccessToken()) {
        config._retried = true
        config.headers = { ...config.headers, Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }
        return service(config)
      }
    }
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

// 类型化封装：拦截器已解包 Result.data，泛型直接返回 Promise<T>
export default {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config) as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config) as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config) as Promise<T>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config) as Promise<T>
  }
}
