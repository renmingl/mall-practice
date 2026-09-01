import request from './request'

// ---------- 类型（与 mall-seckill 后端对齐） ----------

export interface SeckillSession {
  id: number
  name: string
  startTime: string
  endTime: string
  status?: number
  phase?: string
}

export interface SeckillProductItem {
  id: number
  sessionId: number
  spuId: number
  skuId: number
  spuName: string
  spec?: string
  pic?: string
  price: number
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  remainStock: number
}

export interface SeckillRankRow {
  skuId: number
  sales: number
  spuName?: string
  pic?: string
  price?: number
}

// ---------- 秒杀场次 / 商品 ----------

/** 前台场次列表（含 phase：disabled/upcoming/ongoing/finished） */
export function getSeckillSessions() {
  return request.get<SeckillSession[]>('/seckill/sessions')
}

/** 场次商品列表（含剩余库存，未预热回源 DB） */
export function getSeckillProducts(sessionId: number) {
  return request.get<SeckillProductItem[]>('/seckill/sessions/' + sessionId + '/products')
}

/** 秒杀排行榜 Top N（10.4：ZSET rank，member=skuId） */
export function getSeckillRank(sessionId: number, topN = 10) {
  return request.get<SeckillRankRow[]>('/seckill/sessions/' + sessionId + '/rank', { params: { topN } })
}

/** 发放秒杀幂等 token（进入秒杀页时获取，12.3） */
export function getSeckillToken() {
  return request.get<{ token: string }>('/seckill/token')
}

/** 提交秒杀（Lua 原子扣减 + 限购 + MQ 削峰；返回排队标识） */
export function submitSeckill(data: {
  seckillProductId: number
  quantity?: number
  token: string
  receiverName: string
  receiverPhone: string
  receiverAddress: string
}) {
  return request.post<{ queued: boolean; seckillProductId: number; requestId: string; tip: string }>('/seckill/submit', data)
}

/** 秒杀结果轮询（status：0 处理中 / 1 成功含 orderSn / 2 失败含 reason） */
export function querySeckillResult(seckillProductId: number) {
  return request.get<{ status: number; orderSn?: string; reason?: string; tip?: string }>('/seckill/result', {
    params: { seckillProductId }
  })
}

// ---------- 签到（member 服务，需登录） ----------

export interface CheckinStatus {
  date: string
  signedToday: boolean
  monthDays: number
  streakDays: number
}

/** 签到（当天重复幂等） */
export function checkin() {
  return request.post<CheckinStatus>('/member/stats/checkin')
}

/** 签到状态 */
export function getCheckinStatus() {
  return request.get<CheckinStatus>('/member/stats/checkin/status')
}

// ---------- 商品运营（product 服务） ----------

export interface SalesRankRow {
  skuId: number
  sales: number
  spuId?: number
  spuName?: string
  pic?: string
  price?: number
}

/** 商品销量排行榜 Top N（10.4：rank:sales） */
export function getSalesRank(topN = 10) {
  return request.get<SalesRankRow[]>('/product/stats/sales-rank', { params: { topN } })
}

/** 浏览埋点（PV + UV + 足迹，登录用户记录足迹） */
export function trackView(spuId: number) {
  return request.post<void>('/product/stats/view', null, { params: { spuId } })
}

/** 点赞 / 取消 / 状态 / 数量 */
export function likeSpu(spuId: number) {
  return request.post<boolean>(`/product/stats/like/${spuId}`)
}

export function unlikeSpu(spuId: number) {
  return request.delete<void>(`/product/stats/like/${spuId}`)
}

export function getLikeStatus(spuId: number) {
  return request.get<boolean>(`/product/stats/like/status/${spuId}`)
}

export function getLikeCount(spuId: number) {
  return request.get<number>(`/product/stats/like/count/${spuId}`)
}

/** 最近浏览足迹（最多 50 条） */
export function getBrowseHistory() {
  return request.get<{ spuId: number; viewTime: number; spuName?: string; mainPic?: string }[]>('/product/stats/history')
}
