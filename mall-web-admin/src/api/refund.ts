import request from './request'

// ---------- 后台退款审核（mall-payment） ----------

export interface AdminRefundRow {
  id: number
  refundSn: string
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
  applyTime?: string
  refundTime?: string
  createTime: string
}

/** 退款单分页（订单号/状态筛选） */
export function getAdminRefundPage(page = 1, size = 10, orderSn?: string, status?: number) {
  return request.get<{ records: AdminRefundRow[]; total: number }>('/admin/refund/page', {
    params: { page, size, orderSn, status }
  })
}

/** 审核退款申请：approved=true 通过 / false 拒绝 */
export function auditRefund(id: number, approved: boolean, auditBy: string) {
  return request.post<void>(`/admin/refund/${id}/audit`, null, { params: { approved, auditBy } })
}

/** 确认退货（退货退款：审核通过后确认收到退货，执行退款） */
export function confirmReturnRefund(id: number) {
  return request.post<void>(`/admin/refund/${id}/confirm-return`)
}

/** 重试执行退款（仅退款执行失败/超时后的补偿入口；退款单停留 1 或 3 状态时可触发） */
export function retryRefund(id: number) {
  return request.post<void>(`/admin/refund/${id}/retry`)
}
