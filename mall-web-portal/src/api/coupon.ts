import request from './request'

// ---------- 优惠券（mall-coupon；需登录） ----------

export interface CouponTemplateRow {
  id: number
  name: string
  type: number
  amount: number
  threshold: number
  totalCount: number
  receivedCount: number
  perLimit: number
  useStartTime: string
  useEndTime: string
  status: number
  remaining?: number
  myReceived?: number
  receivable?: boolean
}

export interface MyCouponRow {
  id: number
  couponId: number
  name: string
  type: number
  amount: number
  threshold: number
  status: number
  receiveTime: string
  useTime?: string
  expireTime?: string
  orderSn?: string
}

/** 领券中心（进行中 + 未过期 + 未领完；附每人剩余可领数） */
export function getCouponCenter(page = 1, size = 10) {
  return request.get<{ records: CouponTemplateRow[]; total: number }>('/coupon/center', {
    params: { page, size }
  })
}

/** 领券（SETNX 幂等 + 条件更新防超领） */
export function receiveCoupon(couponId: number) {
  return request.post<void>('/coupon/receive', null, { params: { couponId } })
}

/** 我的优惠券（status：0未使用 1已锁定 2已使用 3已过期） */
export function getMyCoupons(status?: number, page = 1, size = 10) {
  return request.get<{ records: MyCouponRow[]; total: number }>('/coupon/mine', {
    params: { status, page, size }
  })
}
