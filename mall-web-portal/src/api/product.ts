import request from './request'

// ---------- 类型（与 mall-product 后端对齐） ----------

export interface CategoryNode {
  id: number
  parentId: number
  name: string
  level?: number
  icon?: string
  children?: CategoryNode[]
}

export interface Brand {
  id: number
  name: string
  logo?: string
}

export interface Spu {
  id: number
  spuCode: string
  categoryId?: number
  brandId?: number
  name: string
  subtitle?: string
  mainPic?: string
  unit?: string
  pics?: string
  detail?: string
  status?: number
  sort?: number
  sales?: number
  /** 最低价（首页热销接口附带） */
  price?: number
  createTime?: string
}

export interface Sku {
  id: number
  spuId: number
  skuCode: string
  spec?: string
  price: number
  stock: number
  status?: number
}

export interface ProductDetail {
  spu: Spu
  skuList: Sku[]
  categoryName?: string
  brandName?: string
}

/** 首页 Banner（运营位） */
export interface Banner {
  id: number
  title: string
  subtitle: string
  color1: string
  color2: string
  emoji: string
  link: string
}

// ---------- 商品 ----------

/** 首页运营 Banner 列表 */
export function getBanners() {
  return request.get<Banner[]>('/portal/banner')
}

/** 前台分类树（仅启用） */
export function getCategories() {
  return request.get<CategoryNode[]>('/product/categories')
}

/** 启用品牌列表（筛选下拉） */
export function getBrands() {
  return request.get<Brand[]>('/product/brands')
}

/** 商品分页（仅上架，分类/品牌/关键词筛选） */
export function getProductPage(
  page = 1,
  size = 10,
  params?: { categoryId?: number; brandId?: number; keyword?: string }
) {
  return request.get<{ records: Spu[]; total: number }>('/product/list', {
    params: { page, size, ...params }
  })
}

/** 商品详情（缓存三防） */
export function getProductDetail(spuId: number) {
  return request.get<ProductDetail>(`/product/detail/${spuId}`)
}

/** 热销 Top N */
export function getHotProducts(limit = 10) {
  return request.get<Spu[]>('/product/hot', { params: { limit } })
}

// ---------- 收藏（需登录，网关透传 X-User-Id） ----------

export function addFavorite(spuId: number) {
  return request.post<void>(`/favorite/${spuId}`)
}

export function removeFavorite(spuId: number) {
  return request.delete<void>(`/favorite/${spuId}`)
}

export function getFavoriteList(page = 1, size = 10) {
  return request.get<{ records: { favoriteId: number; spuId: number; name?: string; subtitle?: string; mainPic?: string; price?: number; createTime: string }[]; total: number }>(
    '/favorite/list',
    { params: { page, size } }
  )
}

export function getFavoriteStatus(spuId: number) {
  return request.get<boolean>(`/favorite/status/${spuId}`)
}
