import request from './request'

// ---------- 类型（与后端 DTO/实体对齐） ----------

export interface MemberProfile {
  id: number
  username: string
  nickname: string
  avatar?: string
  phone?: string
  email?: string
  gender?: number
  birthday?: string
  level?: number
  points?: number
  createTime?: string
}

export interface MemberPointLog {
  id: number
  changeType?: number
  changePoint?: number
  pointAfter?: number
  orderSn?: string
  createTime: string
}

export interface MemberAddress {
  id?: number
  receiverName: string
  receiverPhone: string
  province?: string
  city?: string
  district?: string
  detailAddress: string
  defaultFlag?: number
}

// ---------- 个人资料 ----------

/** 个人资料查询 */
export function getProfile() {
  return request.get<MemberProfile>('/member/profile')
}

/** 修改资料（昵称/头像/邮箱/性别/生日） */
export function updateProfile(data: Partial<MemberProfile>) {
  return request.put<void>('/member/profile', data)
}

/** 积分余额 + 等级权益 */
export function getPoints() {
  return request.get<{
    memberId: number
    points: number
    level: number
    levelInfo: { level: number; name: string; discount: number; pointsRate: number; freeShipping: boolean }
  }>('/member/points')
}

/** 积分流水分页 */
export function getPointLogs(page = 1, size = 10) {
  return request.get<{ records: MemberPointLog[]; total: number }>('/member/point-logs', {
    params: { page, size }
  })
}

// ---------- 收货地址 ----------

export function getAddressList() {
  return request.get<MemberAddress[]>('/member/address')
}

export function addAddress(data: MemberAddress) {
  return request.post<MemberAddress>('/member/address', data)
}

export function updateAddress(id: number, data: MemberAddress) {
  return request.put<void>(`/member/address/${id}`, data)
}

export function deleteAddress(id: number) {
  return request.delete<void>(`/member/address/${id}`)
}

export function setDefaultAddress(id: number) {
  return request.put<void>(`/member/address/${id}/default`)
}
