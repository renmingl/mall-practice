import request from './request'

// ---------- 支付/退款（mall-payment；需登录） ----------

export interface Payment {
  id: number
  paymentSn: string
  orderId: number
  orderSn: string
  memberId: number
  payAmount: number
  payType: number
  tradeNo?: string
  status: number
  notifyTime?: string
  createTime: string
}

export interface RefundApplyPayload {
  orderSn: string
  reason?: string
  refundType: number
  returnCompany?: string
  returnSn?: string
}

export interface RefundRow {
  id: number
  refundSn: string
  orderId: number
  orderSn: string
  paymentSn: string
  memberId: number
  refundAmount: number
  reason?: string
  refundType: number
  returnCompany?: string
  returnSn?: string
  status: number
  auditBy?: string
  auditTime?: string
  applyTime: string
  refundTime?: string
  createTime: string
}

/** 模拟第三方支付回调（演示；重复回调幂等） */
export function mockPayCallback(paymentSn: string, tradeNo?: string) {
  return request.post<Payment>(`/payment/${paymentSn}/mock-callback`, null, { params: { tradeNo } })
}

/** 支付结果查询（收银台/支付结果页轮询；含查单兜底补偿） */
export function queryPayment(orderSn: string) {
  return request.get<Payment>('/payment/query', { params: { orderSn } })
}

/** 申请退款（整单退款：1仅退款 2退货退款） */
export function applyRefund(payload: RefundApplyPayload) {
  return request.post<void>('/payment/refund/apply', payload)
}

/** 我的退款单分页 */
export function getMyRefunds(page = 1, size = 10) {
  return request.get<{ records: RefundRow[]; total: number }>('/payment/refund/list', {
    params: { page, size }
  })
}
