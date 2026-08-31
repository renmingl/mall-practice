import request from './request'

// ---------- 订单（mall-order；需登录） ----------

export interface OrderItem {
  id: number
  orderSn: string
  skuId: number
  spuName: string
  spec?: string
  pic?: string
  price: number
  quantity: number
  totalAmount: number
  commented?: number
}

export interface Order {
  id: number
  orderSn: string
  requestId: string
  memberId: number
  totalAmount: number
  discountAmount: number
  payAmount: number
  status: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
  payType?: number
  payTime?: string
  deliveryCompany?: string
  deliverySn?: string
  deliveryTime?: string
  finishTime?: string
  cancelBy?: string
  cancelReason?: string
  createTime: string
}

export interface OrderRow {
  order: Order
  items: OrderItem[]
}

export interface StatusLog {
  id: number
  orderId: number
  fromStatus: number
  toStatus: number
  operator: string
  remark?: string
  createTime: string
}

export interface OrderDetail {
  order: Order
  items: OrderItem[]
  statusLogs: StatusLog[]
}

export interface OrderCreatePayload {
  requestId: string
  couponUserId?: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
}

/** 下单（requestId 幂等；商品取购物车勾选项，服务端实时校验） */
export function createOrder(payload: OrderCreatePayload) {
  return request.post<Order>('/order/create', payload)
}

/** 取消订单（仅待付款） */
export function cancelOrder(orderSn: string) {
  return request.post<void>(`/order/${orderSn}/cancel`)
}

/** 确认收货（仅待收货） */
export function confirmReceive(orderSn: string) {
  return request.post<void>(`/order/${orderSn}/confirm-receive`)
}

/** 拉起收银台：创建支付流水，返回收银台所需信息 */
export function payOrder(orderSn: string, payType: number) {
  return request.post<{
    id: number
    paymentSn: string
    orderId: number
    orderSn: string
    memberId: number
    payAmount: number
    payType: number
    status: number
  }>(`/order/${orderSn}/pay`, null, { params: { payType } })
}

/** 订单详情（订单头 + 明细 + 状态流水） */
export function getOrderDetail(orderSn: string) {
  return request.get<OrderDetail>(`/order/${orderSn}`)
}

/** 我的订单分页（status：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款） */
export function getMyOrders(status?: number, page = 1, size = 10) {
  return request.get<{ records: OrderRow[]; total: number }>('/order/list', {
    params: { status, page, size }
  })
}
