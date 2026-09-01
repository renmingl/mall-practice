import request from './request'

// ---------- 类型（与 mall-search 后端对齐） ----------

export interface SearchRecord {
  spuId: number
  name: string
  subtitle?: string
  pic?: string
  price?: number
  sales?: number
  categoryName?: string
  brandName?: string
  /** ES 高亮片段（含 <em> 标记，优先展示） */
  highlightName?: string
  highlightSubtitle?: string
}

export interface SearchResult {
  total: number
  records: SearchRecord[]
  /** ES 不可用降级标记（true 表示搜索服务异常返回空结果） */
  fallback?: boolean
  error?: string
}

// ---------- 搜索 ----------

/** 商品搜索：keyword 为空时 ES 走销量榜兜底；categoryId 可选过滤（高亮字段经网关透传） */
export function getSearch(keyword?: string, categoryId?: number, page = 1, size = 10) {
  return request.get<SearchResult>('/search', { params: { keyword, categoryId, page, size } })
}

/** 搜索联想：名称前缀实时返回候选（前 8 条） */
export function getSearchSuggest(prefix: string) {
  return request.get<string[]>('/search/suggest', { params: { prefix } })
}
