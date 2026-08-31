import request from './request'

// ---------- 结算预览（mall-portal 聚合；需登录） ----------

export interface CheckoutItem {
  skuId: number
  quantity: number
  invalid?: boolean
  valid?: boolean
  spuId?: number
  spuName?: string
  spec?: string
  pic?: string
  price?: number
  stock?: number
  status?: number
  spuStatus?: number
}

export interface CheckoutCoupon {
  couponUserId: number
  couponId: number
  name: string
  type: number
  amount: number
  threshold: number
  discountAmount: number
}

export interface CheckoutPreview {
  items: CheckoutItem[]
  totalAmount: number
  availableCoupons: CheckoutCoupon[]
}

/** 结算预览：勾选商品 + 金额 + 可用优惠券（下单仍以 order 实时校验为准） */
export function getCheckoutPreview() {
  return request.get<CheckoutPreview>('/checkout/preview')
}
