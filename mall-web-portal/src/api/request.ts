import axios from 'axios'
import { showToast } from 'vant'

/** 后端统一返回结构（与 mall-common Result<T> 对齐） */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

// axios 封装：baseURL 走 /api 前缀（dev 经 Vite 代理 → 网关 8080）
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

// 请求拦截：携带 X-Trace-Id（本地生成并复用，与后端 TraceIdGlobalFilter 约定一致）
service.interceptors.request.use((config) => {
  let traceId = localStorage.getItem('X-Trace-Id')
  if (!traceId) {
    traceId = crypto.randomUUID().replace(/-/g, '')
    localStorage.setItem('X-Trace-Id', traceId)
  }
  config.headers['X-Trace-Id'] = traceId
  return config
})

// 响应拦截：Result<T> 解包，非 200 统一提示
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    if (res.code !== 200) {
      showToast(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  (error) => {
    showToast(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
