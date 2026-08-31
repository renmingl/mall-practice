import request from './request'

// ---------- 商品评价（mall-product；需登录） ----------

export interface CommentCreatePayload {
  orderItemId: number
  rating: number
  content?: string
  pics?: string[]
}

export interface MyCommentRow {
  id: number
  orderItemId?: number
  spuId: number
  spuName?: string
  skuSpec?: string
  pic?: string
  rating: number
  content?: string
  pics?: string
  reply?: string
  status: number
  createTime: string
}

/** 发表评价（订单已完成 + 订单项未评价；orderItemId 唯一防重复） */
export function createComment(payload: CommentCreatePayload) {
  return request.post<void>('/comment', payload)
}

/** 我的评价分页 */
export function getMyComments(page = 1, size = 10) {
  return request.get<{ records: MyCommentRow[]; total: number }>('/comment/mine', {
    params: { page, size }
  })
}
