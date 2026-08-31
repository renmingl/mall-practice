import request from './request'

// ---------- 购物车（mall-cart，Redis Hash；需登录） ----------

export interface CartRow {
  skuId: number
  quantity: number
  checked: boolean
  invalid?: boolean
  skuCode?: string
  spuId?: number
  spuName?: string
  spec?: string
  pic?: string
  price?: number
  stock?: number
  subtotal?: number
}

/** 购物车列表（合并商品快照，失效商品 invalid=true） */
export function getCartList() {
  return request.get<CartRow[]>('/cart/list')
}

/** 加购 */
export function addToCart(skuId: number, quantity = 1) {
  return request.post<void>('/cart/add', null, { params: { skuId, quantity } })
}

/** 修改数量 */
export function updateCartQuantity(skuId: number, quantity: number) {
  return request.post<void>('/cart/update', null, { params: { skuId, quantity } })
}

/** 批量勾选/取消勾选 */
export function checkCartItems(skuIds: number[], checked: boolean) {
  return request.post<void>('/cart/check', skuIds, { params: { checked } })
}

/** 删除条目 */
export function removeCartItems(skuIds: number[]) {
  return request.delete<void>('/cart/remove', { params: { skuIds } })
}

/** 购物车角标（件数合计） */
export function getCartCount() {
  return request.get<number>('/cart/count')
}
