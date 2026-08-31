import request from './request'

// ---------- 后台评价管理（mall-product） ----------

export interface AdminCommentRow {
  id: number
  orderItemId: number
  orderSn: string
  memberId: number
  spuId: number
  spuName: string
  rating: number
  content?: string
  pics?: string[]
  status: number
  reply?: string
  replyTime?: string
  createTime: string
}

/** 评价分页（商品名称/状态筛选） */
export function getAdminCommentPage(page = 1, size = 10, keyword?: string, status?: number) {
  return request.get<{ records: AdminCommentRow[]; total: number }>('/admin/comment/page', {
    params: { page, size, keyword, status }
  })
}

/** 商家回复 */
export function replyComment(id: number, reply: string) {
  return request.post<void>('/admin/comment/reply', null, { params: { id, reply } })
}

/** 隐藏/显示（0隐藏 1正常） */
export function updateCommentStatus(id: number, status: number) {
  return request.post<void>('/admin/comment/status', null, { params: { id, status } })
}
