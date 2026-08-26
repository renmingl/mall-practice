import request from './request'

// ---------- 类型（与后端 DTO 对齐） ----------

export interface CaptchaData {
  uuid: string
  imgBase64: string
}

export interface LoginUser {
  id: number
  username: string
  nickname: string
  avatar?: string
  phone?: string
  level?: number
  points?: number
  userType?: string
  perms?: string | null
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: LoginUser
}

// ---------- 接口 ----------

/** 图形验证码 */
export function getCaptcha() {
  return request.get<CaptchaData>('/auth/captcha')
}

/** 模拟短信验证码（开发期后端直接返回验证码） */
export function getSmsCode(phone: string) {
  return request.get<{ phone: string; smsCode: string }>('/auth/captcha/sms', { params: { phone } })
}

/** 买家登录 */
export function login(data: { username: string; password: string; captchaUuid: string; captchaCode: string }) {
  return request.post<LoginResult>('/auth/login', data)
}

/** 买家注册（注册即登录） */
export function register(data: {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
  captchaUuid: string
  captchaCode: string
}) {
  return request.post<LoginResult>('/auth/register', data)
}

/** 刷新令牌 */
export function refresh(refreshToken: string) {
  return request.post<LoginResult>('/auth/refresh', { refreshToken })
}

/** 退出登录（access token 进黑名单） */
export function logout() {
  return request.post<void>('/auth/logout')
}

/** 找回密码（短信验证码） */
export function forgotPassword(data: { phone: string; smsCode: string; newPassword: string }) {
  return request.post<void>('/auth/forgot-password', data)
}
