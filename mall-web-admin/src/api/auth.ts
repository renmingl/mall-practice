import request from './request'

// ---------- 类型（与后端对齐） ----------

export interface AdminLoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: {
    id: number
    username: string
    nickname: string
    avatar?: string
    userType: string
    roles: string[]
    perms: string[]
  }
}

export interface AdminMenu {
  id?: number
  parentId: number
  name: string
  type: number
  path?: string
  perms?: string
  icon?: string
  sort?: number
  status?: number
  children?: AdminMenu[]
}

// ---------- 接口 ----------

/** 获取图形验证码（uuid + base64 图片；后台登录/买家登录共用同一验证码体系） */
export function getAdminCaptcha() {
  return request.get<{ uuid: string; imgBase64: string }>('/auth/captcha')
}

/** 后台登录（图形验证码防暴力破解） */
export function adminLogin(data: { username: string; password: string; captchaUuid: string; captchaCode: string }) {
  return request.post<AdminLoginResult>('/auth/admin/login', data)
}

/** 当前登录管理员信息（含角色/权限，刷新页面恢复登录态用） */
export function getAdminMe() {
  return request.get<{ id: number; username: string; nickname: string; avatar?: string; roles: string[]; perms: string[] }>(
    '/auth/admin/me'
  )
}

/** 退出登录（access token 进黑名单） */
export function adminLogout() {
  return request.post<void>('/auth/logout')
}

// ---------- 用户管理 ----------

export interface AdminUser {
  id?: number
  username?: string
  password?: string
  nickname?: string
  phone?: string
  email?: string
  status?: number
  lastLoginTime?: string
  createTime?: string
}

export function getUserPage(page = 1, size = 10, username?: string, status?: number) {
  return request.get<{ records: AdminUser[]; total: number }>('/admin/user/page', {
    params: { page, size, username, status }
  })
}

export function addUser(data: AdminUser) {
  return request.post<void>('/admin/user', data)
}

export function updateUser(data: AdminUser) {
  return request.put<void>('/admin/user', data)
}

export function deleteUser(id: number) {
  return request.delete<void>(`/admin/user/${id}`)
}

export function resetUserPassword(id: number, newPassword: string) {
  return request.put<void>(`/admin/user/${id}/password`, { newPassword })
}

export function getUserRoles(id: number) {
  return request.get<AdminRole[]>('/admin/user/' + id + '/roles')
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put<void>(`/admin/user/${id}/roles`, { roleIds })
}

// ---------- 角色管理 ----------

export interface AdminRole {
  id?: number
  name: string
  code?: string
  description?: string
  status?: number
  createTime?: string
}

export function getRoleList() {
  return request.get<AdminRole[]>('/admin/role/list')
}

export function getRolePage(page = 1, size = 10, name?: string) {
  return request.get<{ records: AdminRole[]; total: number }>('/admin/role/page', {
    params: { page, size, name }
  })
}

export function addRole(data: AdminRole) {
  return request.post<void>('/admin/role', data)
}

export function updateRole(data: AdminRole) {
  return request.put<void>('/admin/role', data)
}

export function deleteRole(id: number) {
  return request.delete<void>(`/admin/role/${id}`)
}

export function getRoleMenuIds(id: number) {
  return request.get<number[]>(`/admin/role/${id}/menus`)
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return request.put<void>(`/admin/role/${id}/menus`, { menuIds })
}

// ---------- 菜单管理 ----------

export function getMenuTree() {
  return request.get<AdminMenu[]>('/admin/menu/tree')
}

export function getMenuList() {
  return request.get<AdminMenu[]>('/admin/menu/list')
}

export function addMenu(data: AdminMenu) {
  return request.post<void>('/admin/menu', data)
}

export function updateMenu(data: AdminMenu) {
  return request.put<void>('/admin/menu', data)
}

export function deleteMenu(id: number) {
  return request.delete<void>(`/admin/menu/${id}`)
}
