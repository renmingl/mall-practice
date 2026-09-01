import request from './request'

// ---------- 类型（与 mall-product 后端 DTO/实体对齐） ----------

export interface CategoryNode {
  id?: number
  parentId: number
  name: string
  level?: number
  icon?: string
  sort?: number
  status?: number
  children?: CategoryNode[]
}

export interface Brand {
  id?: number
  name: string
  logo?: string
  description?: string
  sort?: number
  status?: number
  createTime?: string
}

export interface Sku {
  id?: number
  spuId?: number
  skuCode: string
  spec?: string
  price?: number
  stock?: number
  lowStock?: number
  status?: number
  version?: number
}

export interface Spu {
  id?: number
  spuCode: string
  categoryId?: number
  brandId?: number
  name: string
  subtitle?: string
  mainPic?: string
  pics?: string
  unit?: string
  detail?: string
  status?: number
  sort?: number
  sales?: number
  createTime?: string
}

export interface SpuSaveDTO {
  id?: number
  spuCode: string
  categoryId?: number
  brandId?: number
  name: string
  subtitle?: string
  mainPic?: string
  pics?: string
  unit?: string
  detail?: string
  status?: number
  sort?: number
  skuList?: Sku[]
}

export interface Supplier {
  id?: number
  name: string
  contact?: string
  phone?: string
  address?: string
  remark?: string
  status?: number
  createTime?: string
}

export interface Purchase {
  id?: number
  purchaseSn?: string
  supplierId?: number
  totalAmount?: number
  status?: number
  auditBy?: string
  auditTime?: string
  createTime?: string
}

export interface PurchaseItem {
  id?: number
  purchaseId?: number
  skuId: number
  quantity: number
  receivedQuantity?: number
  purchasePrice?: number
}

export interface StockLog {
  id: number
  skuId: number
  bizSn?: string
  changeType: number
  changeCount: number
  stockBefore: number
  stockAfter: number
  createTime: string
}

export interface StockRow {
  id: number
  skuCode: string
  spuId: number
  spuName: string
  spec?: string
  price?: number
  stock: number
  lowStock?: number
  warning: boolean
  status?: number
  updateTime?: string
}

// ---------- 分类 ----------

export function getCategoryTree() {
  return request.get<CategoryNode[]>('/admin/category/tree')
}

export function addCategory(data: CategoryNode) {
  return request.post<void>('/admin/category', data)
}

export function updateCategory(data: CategoryNode) {
  return request.put<void>('/admin/category', data)
}

export function deleteCategory(id: number) {
  return request.delete<void>(`/admin/category/${id}`)
}

export function updateCategoryStatus(id: number, status: number) {
  return request.put<void>(`/admin/category/${id}/status`, null, { params: { status } })
}

// ---------- 品牌 ----------

export function getBrandPage(page = 1, size = 10, name?: string, status?: number) {
  return request.get<{ records: Brand[]; total: number }>('/admin/brand/list', {
    params: { page, size, name, status }
  })
}

export function addBrand(data: Brand) {
  return request.post<void>('/admin/brand', data)
}

export function updateBrand(data: Brand) {
  return request.put<void>('/admin/brand', data)
}

export function deleteBrand(id: number) {
  return request.delete<void>(`/admin/brand/${id}`)
}

export function updateBrandStatus(id: number, status: number) {
  return request.put<void>(`/admin/brand/${id}/status`, null, { params: { status } })
}

// ---------- 商品 ----------

export function getProductPage(
  page = 1,
  size = 10,
  params?: { spuCode?: string; name?: string; categoryId?: number; status?: number }
) {
  return request.get<{ records: Spu[]; total: number }>('/admin/product/list', {
    params: { page, size, ...params }
  })
}

export function getProductDetail(id: number) {
  return request.get<{ spu: Spu; skuList: Sku[]; categoryName?: string; brandName?: string }>(`/admin/product/${id}`)
}

export function saveProduct(data: SpuSaveDTO) {
  return request.post<void>('/admin/product', data)
}

export function updateProductStatus(id: number, status: number) {
  return request.put<void>(`/admin/product/${id}/status`, null, { params: { status } })
}

export function deleteProduct(id: number) {
  return request.delete<void>(`/admin/product/${id}`)
}

export function preloadProductCache() {
  return request.post<number>('/admin/product/preload')
}

// ---------- 供应商 ----------

export function getSupplierPage(page = 1, size = 10, name?: string, status?: number) {
  return request.get<{ records: Supplier[]; total: number }>('/admin/supplier/list', {
    params: { page, size, name, status }
  })
}

export function addSupplier(data: Supplier) {
  return request.post<void>('/admin/supplier', data)
}

export function updateSupplier(data: Supplier) {
  return request.put<void>('/admin/supplier', data)
}

export function deleteSupplier(id: number) {
  return request.delete<void>(`/admin/supplier/${id}`)
}

export function updateSupplierStatus(id: number, status: number) {
  return request.put<void>(`/admin/supplier/${id}/status`, null, { params: { status } })
}

// ---------- 采购单 ----------

export function getPurchasePage(page = 1, size = 10, status?: number, supplierId?: number) {
  return request.get<{ records: Purchase[]; total: number }>('/admin/purchase/list', {
    params: { page, size, status, supplierId }
  })
}

export function getPurchaseDetail(id: number) {
  return request.get<{ purchase: Purchase; supplierName?: string; items: PurchaseItem[] }>(`/admin/purchase/${id}`)
}

export function createPurchase(data: { supplierId: number; items: { skuId: number; quantity: number; purchasePrice: number }[] }) {
  return request.post<number>('/admin/purchase', data)
}

export function auditPurchase(id: number, pass: boolean) {
  return request.put<void>(`/admin/purchase/${id}/audit`, null, { params: { pass } })
}

export function cancelPurchase(id: number) {
  return request.put<void>(`/admin/purchase/${id}/cancel`)
}

export function receivePurchase(data: { itemId: number; quantity: number }) {
  return request.post<void>('/admin/purchase/receive', data)
}

// ---------- 库存 ----------

export function getStockPage(page = 1, size = 10, keyword?: string) {
  return request.get<{ records: StockRow[]; total: number }>('/admin/stock/list', {
    params: { page, size, keyword }
  })
}

export function getStockLogs(skuId?: number, page = 1, size = 10) {
  return request.get<{ records: StockLog[]; total: number }>('/admin/stock/logs', {
    params: { skuId, page, size }
  })
}

export function getStockWarnings() {
  return request.get<{ id: number; skuCode: string; spuId: number; stock: number; lowStock: number }[]>(
    '/admin/stock/warning'
  )
}

export function checkStock(data: { skuId: number; stock: number; remark?: string }) {
  return request.put<void>('/admin/stock/check', data)
}

// ---------- SKU 搜索（阶段 7 秒杀商品配置选品） ----------

export function searchSkuList(keyword: string) {
  return request.get<
    { id: number; skuCode: string; spuId: number; spuName: string; spec?: string; price?: number; stock?: number; status?: number }[]
  >('/admin/sku/search', { params: { keyword } })
}

// ---------- 图片上传 ----------

export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<string>('/admin/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
