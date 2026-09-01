import request from './request'

// ---------- 数据看板（mall-admin 聚合） ----------

export interface TodayStats {
  orderCount?: number
  salesAmount?: number
  seckillOrderCount?: number
}

export interface MemberStats {
  online?: number
  dau?: number
  checkinToday?: number
  newMembersToday?: number
}

export interface TrendRow {
  date: string
  orderCount: number
  salesAmount: number
}

export interface SalesRankRow {
  skuId: number
  sales: number
  spuId?: number
  spuName?: string
  pic?: string
  price?: number
}

export interface ViewsRankRow {
  spuId: number
  pv: number
  spuName?: string
  mainPic?: string
}

export interface StockWarningRow {
  id: number
  skuCode: string
  spuId?: number
  stock: number
  lowStock: number
}

export interface DashboardSummary {
  today?: TodayStats
  member?: MemberStats
  trend7d?: TrendRow[]
  salesRank?: SalesRankRow[]
  viewsRank?: ViewsRankRow[]
  warnings?: StockWarningRow[]
}

/** 看板总览（今日概览 / 会员运营 / 7 天趋势 / 销量榜 / 浏览榜 / 库存预警） */
export function getDashboardSummary() {
  return request.get<DashboardSummary>('/admin/dashboard/summary')
}
