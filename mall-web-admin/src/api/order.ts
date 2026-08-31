import request from './request'

// ---------- 后台订单管理（mall-order） ----------

export interface AdminOrderRow {
  id: number
  orderSn: string
  requestId: string
  memberId: number
  memberName?: string
  totalAmount: number
  freightAmount: number
  couponAmount: number
  discountAmount: number
  payAmount: number
  payType: number
  status: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
  payTime?: string
  deliveryCompany?: string
  deliverySn?: string
  deliveryTime?: string
  receiveTime?: string
  createTime: string
}

export interface AdminOrderItemRow {
  id: number
  orderId: number
  orderSn: string
  spuId: number
  spuName: string
  skuId: number
  skuCode: string
  spec?: string
  pic?: string
  price: number
  quantity: number
  subtotal: number
}

/** page 记录：order 主单 + items 明细 */
export interface AdminOrderPageRow {
  order: AdminOrderRow
  items: AdminOrderItemRow[]
}

/** 订单分页（订单号/状态筛选） */
export function getAdminOrderPage(page = 1, size = 10, orderSn?: string, status?: number) {
  return request.get<{ records: AdminOrderPageRow[]; total: number }>('/admin/order/page', {
    params: { page, size, orderSn, status }
  })
}

/** 发货：1待发货 → 2待收货 + 物流信息 */
export function deliverOrder(orderId: number, company: string, sn: string) {
  return request.post<void>('/admin/order/deliver', null, { params: { orderId, company, sn } })
}
