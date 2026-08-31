import request from './request'

// ---------- 后台优惠券模板（mall-coupon） ----------

export interface CouponTemplate {
  id: number
  name: string
  type: number
  amount: number
  threshold: number
  totalCount: number
  perLimit: number
  receivedCount: number
  useStartTime: string
  useEndTime: string
  status: number
  createTime: string
}

export interface CouponSavePayload {
  id?: number
  name: string
  type: number
  amount: number
  threshold?: number
  totalCount: number
  perLimit: number
  useStartTime: string
  useEndTime: string
}

/** 模板分页（名称/状态筛选） */
export function getCouponTemplatePage(page = 1, size = 10, name?: string, status?: number) {
  return request.get<{ records: CouponTemplate[]; total: number }>('/admin/coupon/page', {
    params: { page, size, name, status }
  })
}

/** 新增/修改模板 */
export function saveCouponTemplate(data: CouponSavePayload) {
  return request.post<void>('/admin/coupon/save', data)
}

/** 模板状态：1进行中 0已结束 */
export function updateCouponTemplateStatus(id: number, status: number) {
  return request.post<void>('/admin/coupon/status', null, { params: { id, status } })
}
