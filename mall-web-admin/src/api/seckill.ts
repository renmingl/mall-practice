import request from './request'

// ---------- 秒杀后台管理（mall-seckill） ----------

export interface SeckillSessionRow {
  id: number
  name: string
  startTime: string
  endTime: string
  status: number
  phase?: string
}

export interface SeckillSessionSave {
  id?: number
  name: string
  startTime: string
  endTime: string
  status?: number
}

export interface SeckillProductRow {
  id: number
  sessionId: number
  spuId: number
  skuId: number
  spuName?: string
  skuCode?: string
  spec?: string
  pic?: string
  price?: number
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  skuStock?: number
  status: number
}

export interface SeckillProductSave {
  id?: number
  sessionId: number
  skuId: number
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  status?: number
}

/** 场次分页（keyword 名称 / status 状态筛选） */
export function getSessionPage(page = 1, size = 10, keyword?: string, status?: number) {
  return request.get<{ records: SeckillSessionRow[]; total: number }>('/admin/seckill/session/page', {
    params: { page, size, keyword, status }
  })
}

/** 保存场次（新增/修改） */
export function saveSession(data: SeckillSessionSave) {
  return request.post<void>('/admin/seckill/session/save', data)
}

/** 启停场次（0禁用 1启用） */
export function toggleSession(id: number, status: number) {
  return request.post<void>(`/admin/seckill/session/${id}/toggle`, null, { params: { status } })
}

/** 手动预热场次（Redis 秒杀库存预热，14.3） */
export function preheatSession(id: number) {
  return request.post<void>(`/admin/seckill/session/${id}/preheat`)
}

/** 秒杀商品分页（按场次/状态筛选） */
export function getProductPage(page = 1, size = 10, sessionId?: number, status?: number) {
  return request.get<{ records: SeckillProductRow[]; total: number }>('/admin/seckill/product/page', {
    params: { page, size, sessionId, status }
  })
}

/** 场次下启用商品列表（下拉选择用） */
export function getSessionProductList(sessionId: number) {
  return request.get<SeckillProductRow[]>('/admin/seckill/product/list', { params: { sessionId } })
}

/** 保存秒杀商品配置（新增/修改） */
export function saveProduct(data: SeckillProductSave) {
  return request.post<void>('/admin/seckill/product/save', data)
}

/** 启停秒杀商品（0禁用 1启用） */
export function toggleProduct(id: number, status: number) {
  return request.post<void>(`/admin/seckill/product/${id}/toggle`, null, { params: { status } })
}

/** 删除秒杀商品配置（场次未开始才允许） */
export function deleteProduct(id: number) {
  return request.post<void>(`/admin/seckill/product/${id}/delete`)
}
