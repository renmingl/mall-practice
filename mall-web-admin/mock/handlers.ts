import type { MockHandler } from './plugin'
import {
  menuTree, users, roles, roleMenus, userRoles,
  categories, brands, spus, skus, spuById, skuById,
  suppliers, purchases, purchaseItems, stockLogs,
  orders, orderItems, refunds, couponTemplates,
  seckillSessions, seckillProducts, comments, dashboard,
  aiSessions, aiMessagesBySession,
  clone, paginate, fmtDateTime
} from './db'

// ---------- 验证码 / 认证 ----------

function captchaImg(code: string): string {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="40">` +
    `<rect width="120" height="40" fill="#f2f4f7"/>` +
    `<text x="18" y="28" font-size="22" font-family="Arial" font-weight="bold" fill="#2d5aa8" transform="rotate(-6 40 28)">${code[0]}</text>` +
    `<text x="42" y="26" font-size="20" font-family="Arial" font-weight="bold" fill="#c0392b" transform="rotate(5 60 26)">${code[1]}</text>` +
    `<text x="62" y="29" font-size="22" font-family="Arial" font-weight="bold" fill="#1e8449" transform="rotate(-4 80 29)">${code[2]}</text>` +
    `<text x="86" y="27" font-size="21" font-family="Arial" font-weight="bold" fill="#8e44ad" transform="rotate(6 100 27)">${code[3]}</text>` +
    `<line x1="8" y1="12" x2="112" y2="30" stroke="#bdc3c7" stroke-width="1"/>` +
    `<line x1="20" y1="32" x2="100" y2="10" stroke="#bdc3c7" stroke-width="1"/>` +
    `</svg>`
  return 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64')
}

const adminUser = {
  id: 1,
  username: 'admin',
  nickname: '超级管理员',
  avatar: '',
  userType: 'ADMIN',
  roles: ['SUPER_ADMIN'],
  perms: ['*']
}

function loginResult() {
  return {
    accessToken: 'mock-admin-token-' + Date.now(),
    refreshToken: 'mock-admin-refresh-' + Date.now(),
    expiresIn: 7200,
    user: clone(adminUser)
  }
}

// ---------- 工具 ----------

/** 菜单树拍平（菜单管理表格用） */
function flatMenus(nodes: typeof menuTree, depth = 0): Record<string, unknown>[] {
  const out: Record<string, unknown>[] = []
  for (const m of nodes) {
    out.push({ ...clone(m), depth, children: undefined })
    if (m.children.length) out.push(...flatMenus(m.children as never, depth + 1))
  }
  return out
}

function nextId(list: { id: number }[]) {
  return Math.max(0, ...list.map((x) => x.id)) + 1
}

/** 商品列表行（分页记录含 skuList） */
function adminProductRow(spu: (typeof spus)[number]) {
  return {
    ...clone(spu),
    skuList: skus.filter((s) => s.spuId === spu.id).map((s) => clone(s))
  }
}

function stockRows() {
  return skus.map((s) => {
    const spu = spuById(s.spuId)
    return {
      id: s.id,
      skuCode: s.skuCode,
      spuId: s.spuId,
      spuName: spu?.name || '',
      spec: s.spec,
      price: s.price,
      stock: s.stock,
      lowStock: s.lowStock,
      warning: s.stock <= s.lowStock,
      status: s.status,
      updateTime: fmtDateTime(new Date())
    }
  })
}

// ---------- AI 助手 Mock ----------

/** 模型清单：与真实 /api/ai/config 同构；mock 仅 DeepSeek 可用，其余置灰展示选择器能力边界 */
function aiMockModels() {
  return [
    { provider: 'deepseek', label: 'DeepSeek', model: 'deepseek-chat', available: true },
    { provider: 'qwen', label: '通义千问', model: 'qwen-plus', available: false },
    { provider: 'openai', label: 'OpenAI', model: 'gpt-4o-mini', available: false },
    { provider: 'zhipu', label: '智谱 GLM', model: 'glm-4-flash', available: false }
  ]
}

/** 关键词命中演示回复（口径与看板 mock 数据一致） */
function aiMockReply(question: string): string {
  const q = question || ''
  let body =
    '你好，我是 mall-practice AI 助手。请以管理员身份向我提问，例如「今天订单量怎么样」「哪些商品库存预警」「销量排行」等。'
  if (/订单|销售|卖|交易/.test(q)) {
    body = '截至当前，今日共产生订单 128 单，销售额 ¥45,680.50，其中秒杀订单 23 单；近 7 天整体呈上升趋势。'
  } else if (/库存|预警|缺货|补货/.test(q)) {
    body = '当前有 3 个 SKU 触发库存预警：SKU20260827801（8 件）、SKU20260827131（6 件）、SKU20260827141（4 件），建议尽快发起采购补货。'
  } else if (/销量|排行|热销|畅销/.test(q)) {
    body = '近 30 天销量 Top5：漫步者 NeoBuds Pro 2（12,780）、小米无线鼠标（15,678）、小米 14（8,932）、索尼 WH-1000XM5（6,543）、小米机械键盘（4,321）。'
  } else if (/会员|用户|运营/.test(q)) {
    body = '当前在线会员 156 人，今日活跃 3,203 人，签到 1,876 人，新增注册 45 人。'
  } else if (/你好|hi|hello|你是谁/.test(q)) {
    body = '你好，我是 mall-practice 电商微服务学习项目的 AI 助手，可以帮你查订单、库存、销量等运营数据，也可以解答项目架构问题。'
  }
  return body + '（Mock 演示回复：启动真实后端并配置模型 API Key 后，由大模型实时生成）'
}

/** 回复文本拆为 SSE 增量帧（每 4 字一帧；与 mall-ai 帧协议一致） */
function aiSseFrames(text: string): string[] {
  const frames: string[] = []
  for (let i = 0; i < text.length; i += 4) {
    frames.push(JSON.stringify({ delta: text.slice(i, i + 4) }))
  }
  frames.push(JSON.stringify({ done: true }))
  return frames
}

// ---------- Handler 注册表 ----------

export const handlers: MockHandler[] = [
  // ---- 认证 ----
  { method: 'GET', url: '/auth/captcha', handler: () => ({ uuid: 'mock-captcha-uuid', imgBase64: captchaImg('8888') }) },
  {
    method: 'POST', url: '/auth/admin/login', handler: (ctx) => {
      const b = ctx.body as { username?: string; password?: string }
      if (!b?.username || !b?.password) return { _raw: { code: 401, message: '用户名或密码不能为空' } }
      return loginResult()
    }
  },
  { method: 'GET', url: '/auth/admin/me', handler: () => clone(adminUser) },
  { method: 'POST', url: '/auth/logout', handler: () => null },

  // ---- 用户管理 ----
  {
    method: 'GET', url: '/admin/user/page', handler: (ctx) => {
      let list = [...users]
      if (ctx.query.username) list = list.filter((u) => u.username.includes(ctx.query.username))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((u) => u.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/user', handler: (ctx) => {
      const b = ctx.body as Record<string, unknown>
      users.push({ id: nextId(users), username: 'user' + nextId(users), status: 1, createTime: fmtDateTime(new Date()), ...b } as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/user', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      const u = users.find((x) => x.id === b?.id)
      if (u) Object.assign(u, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/admin/user/:id', handler: () => null },
  {
    method: 'PUT', url: '/admin/user/:id/password', handler: () => null
  },
  { method: 'GET', url: '/admin/user/:id/roles', handler: (ctx) => { const ids = userRoles[Number(ctx.params.id)] || []; return ids.map((rid) => roles.find((r) => r.id === rid)).filter(Boolean) } },
  {
    method: 'PUT', url: '/admin/user/:id/roles', handler: (ctx) => {
      const b = ctx.body as { roleIds?: number[] }
      userRoles[Number(ctx.params.id)] = b?.roleIds || []
      return null
    }
  },

  // ---- 角色管理 ----
  { method: 'GET', url: '/admin/role/list', handler: () => clone(roles) },
  {
    method: 'GET', url: '/admin/role/page', handler: (ctx) => {
      let list = [...roles]
      if (ctx.query.name) list = list.filter((r) => r.name.includes(ctx.query.name))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/role', handler: (ctx) => {
      const b = ctx.body as Record<string, unknown>
      roles.push({ id: nextId(roles), status: 1, createTime: fmtDateTime(new Date()), ...b } as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/role', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      const r = roles.find((x) => x.id === b?.id)
      if (r) Object.assign(r, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/admin/role/:id', handler: () => null },
  { method: 'GET', url: '/admin/role/:id/menus', handler: (ctx) => roleMenus[Number(ctx.params.id)] || [] },
  {
    method: 'PUT', url: '/admin/role/:id/menus', handler: (ctx) => {
      const b = ctx.body as { menuIds?: number[] }
      roleMenus[Number(ctx.params.id)] = b?.menuIds || []
      return null
    }
  },

  // ---- 菜单管理 ----
  { method: 'GET', url: '/admin/menu/tree', handler: () => clone(menuTree) },
  { method: 'GET', url: '/admin/menu/list', handler: () => flatMenus(menuTree) },
  {
    method: 'POST', url: '/admin/menu', handler: (ctx) => {
      const b = ctx.body as { parentId?: number; name?: string }
      const parent = menuTree.find((m) => m.id === b?.parentId)
      const node = { id: 100 + nextId(flatMenus(menuTree) as { id: number }[]), parentId: b?.parentId || 0, name: b?.name || '新菜单', type: 2, path: '', perms: null, icon: null, sort: 99, status: 1, children: [] }
      if (parent) parent.children.push(node as never)
      else menuTree.push(node as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/menu', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      const all = flatMenus(menuTree)
      const target = all.find((m) => m.id === b?.id)
      if (target) Object.assign(target, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/admin/menu/:id', handler: () => null },

  // ---- 数据看板 ----
  { method: 'GET', url: '/admin/dashboard/summary', handler: () => clone(dashboard) },

  // ---- 分类 ----
  { method: 'GET', url: '/admin/category/tree', handler: () => clone(categories) },
  {
    method: 'POST', url: '/admin/category', handler: (ctx) => {
      const b = ctx.body as { parentId?: number; name?: string }
      const parent = categories.find((c) => c.id === b?.parentId)
      const node = { id: nextId(categories.flatMap((c) => [c, ...c.children])) + 100, parentId: b?.parentId || 0, name: b?.name || '新分类', level: 1, icon: '', sort: 99, status: 1, children: [] }
      if (parent) parent.children.push(node as never)
      else categories.push(node as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/category', handler: (ctx) => {
      const b = ctx.body as { id?: number; name?: string }
      const all = categories.flatMap((c) => [c, ...c.children])
      const t = all.find((c) => c.id === b?.id)
      if (t) t.name = b?.name || t.name
      return null
    }
  },
  { method: 'DELETE', url: '/admin/category/:id', handler: () => null },
  {
    method: 'PUT', url: '/admin/category/:id/status', handler: (ctx) => {
      const all = categories.flatMap((c) => [c, ...c.children])
      const t = all.find((c) => c.id === Number(ctx.params.id))
      if (t) t.status = Number(ctx.query.status)
      return null
    }
  },

  // ---- 品牌 ----
  {
    method: 'GET', url: '/admin/brand/list', handler: (ctx) => {
      let list = [...brands]
      if (ctx.query.name) list = list.filter((b) => b.name.includes(ctx.query.name))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((b) => b.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/brand', handler: (ctx) => {
      const b = ctx.body as Record<string, unknown>
      brands.push({ id: nextId(brands), status: 1, sort: 99, createTime: fmtDateTime(new Date()), ...b } as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/brand', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      const t = brands.find((x) => x.id === b?.id)
      if (t) Object.assign(t, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/admin/brand/:id', handler: () => null },
  {
    method: 'PUT', url: '/admin/brand/:id/status', handler: (ctx) => {
      const t = brands.find((x) => x.id === Number(ctx.params.id))
      if (t) t.status = Number(ctx.query.status)
      return null
    }
  },

  // ---- 商品 ----
  {
    method: 'GET', url: '/admin/product/list', handler: (ctx) => {
      let list = [...spus].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.name) list = list.filter((s) => s.name.includes(ctx.query.name))
      if (ctx.query.spuCode) list = list.filter((s) => s.spuCode.includes(ctx.query.spuCode))
      if (ctx.query.categoryId) list = list.filter((s) => String(s.categoryId) === ctx.query.categoryId)
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((s) => s.status === Number(ctx.query.status))
      return paginate(list.map(adminProductRow), Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'GET', url: '/admin/product/:id', handler: (ctx) => {
      const spu = spuById(Number(ctx.params.id))
      if (!spu) return null
      return {
        spu: clone(spu),
        skuList: skus.filter((s) => s.spuId === spu.id).map((s) => clone(s)),
        categoryName: categories.flatMap((c) => c.children).find((c) => c.id === spu.categoryId)?.name,
        brandName: brands.find((b) => b.id === spu.brandId)?.name
      }
    }
  },
  {
    method: 'POST', url: '/admin/product', handler: (ctx) => {
      const b = ctx.body as { id?: number; name?: string; skuList?: { id?: number; skuCode?: string; spec?: string; price?: number; stock?: number; lowStock?: number }[] }
      if (b?.id) {
        const spu = spuById(b.id)
        if (spu) Object.assign(spu, ctx.body || {})
      } else {
        const id = nextId(spus)
        spus.push({ id, spuCode: 'SPU' + Date.now(), categoryId: 11, brandId: 1, mainPic: '/mock-imgs/p1.svg', pics: '', unit: '件', detail: '', status: 1, sort: 99, sales: 0, createTime: fmtDateTime(new Date()), ...(ctx.body as object) } as never)
        const b2 = ctx.body as { skuList?: { skuCode?: string; spec?: string; price?: number; stock?: number; lowStock?: number }[] }
        if (b2?.skuList?.length) {
          b2.skuList.forEach((s, i) => {
            skus.push({ id: nextId(skus), spuId: id, skuCode: s.skuCode || 'SKU' + Date.now() + i, spec: s.spec || '', price: s.price || 0, stock: s.stock || 0, lowStock: s.lowStock || 5, status: 1, version: 1 })
          })
        }
      }
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/product/:id/status', handler: (ctx) => {
      const spu = spuById(Number(ctx.params.id))
      if (spu) spu.status = Number(ctx.query.status)
      return null
    }
  },
  { method: 'DELETE', url: '/admin/product/:id', handler: () => null },
  { method: 'POST', url: '/admin/product/preload', handler: () => spus.filter((s) => s.status === 1).length },

  // ---- 供应商 ----
  {
    method: 'GET', url: '/admin/supplier/list', handler: (ctx) => {
      let list = [...suppliers]
      if (ctx.query.name) list = list.filter((s) => s.name.includes(ctx.query.name))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((s) => s.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/supplier', handler: (ctx) => {
      const b = ctx.body as Record<string, unknown>
      suppliers.push({ id: nextId(suppliers), status: 1, createTime: fmtDateTime(new Date()), ...b } as never)
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/supplier', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      const t = suppliers.find((x) => x.id === b?.id)
      if (t) Object.assign(t, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/admin/supplier/:id', handler: () => null },
  {
    method: 'PUT', url: '/admin/supplier/:id/status', handler: (ctx) => {
      const t = suppliers.find((x) => x.id === Number(ctx.params.id))
      if (t) t.status = Number(ctx.query.status)
      return null
    }
  },

  // ---- 采购单 ----
  {
    method: 'GET', url: '/admin/purchase/list', handler: (ctx) => {
      let list = [...purchases].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((p) => p.status === Number(ctx.query.status))
      if (ctx.query.supplierId) list = list.filter((p) => p.supplierId === Number(ctx.query.supplierId))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'GET', url: '/admin/purchase/:id', handler: (ctx) => {
      const p = purchases.find((x) => x.id === Number(ctx.params.id))
      if (!p) return null
      return {
        purchase: clone(p),
        supplierName: suppliers.find((s) => s.id === p.supplierId)?.name,
        items: purchaseItems.filter((i) => i.purchaseId === p.id).map((i) => {
          const sku = skuById(i.skuId)
          return { ...clone(i), skuCode: sku?.skuCode, spuName: sku ? spuById(sku.spuId)?.name : '', spec: sku?.spec }
        })
      }
    }
  },
  {
    method: 'POST', url: '/admin/purchase', handler: (ctx) => {
      const b = ctx.body as { supplierId?: number; items?: { skuId: number; quantity: number; purchasePrice: number }[] }
      const id = nextId(purchases)
      const items = b?.items || []
      const totalAmount = items.reduce((n, i) => n + i.purchasePrice * i.quantity, 0)
      purchases.unshift({ id, purchaseSn: 'CG' + Date.now(), supplierId: b?.supplierId || 1, totalAmount, status: 0, createTime: fmtDateTime(new Date()) })
      items.forEach((i, idx) => purchaseItems.push({ id: nextId(purchaseItems), purchaseId: id, skuId: i.skuId, quantity: i.quantity, receivedQuantity: 0, purchasePrice: i.purchasePrice }))
      return id
    }
  },
  {
    method: 'PUT', url: '/admin/purchase/:id/audit', handler: (ctx) => {
      const p = purchases.find((x) => x.id === Number(ctx.params.id))
      if (p) { p.status = ctx.query.pass === 'true' ? 1 : 3; p.auditBy = 'admin'; p.auditTime = fmtDateTime(new Date()) }
      return null
    }
  },
  {
    method: 'PUT', url: '/admin/purchase/:id/cancel', handler: (ctx) => {
      const p = purchases.find((x) => x.id === Number(ctx.params.id))
      if (p) p.status = 4
      return null
    }
  },
  {
    method: 'POST', url: '/admin/purchase/receive', handler: (ctx) => {
      const b = ctx.body as { itemId?: number; quantity?: number }
      const item = purchaseItems.find((i) => i.id === b?.itemId)
      if (item) {
        item.receivedQuantity = Math.min(item.quantity, item.receivedQuantity + (b?.quantity || 0))
        const sku = skuById(item.skuId)
        if (sku) sku.stock += b?.quantity || 0
      }
      return null
    }
  },

  // ---- 库存 ----
  {
    method: 'GET', url: '/admin/stock/list', handler: (ctx) => {
      let list = stockRows()
      if (ctx.query.keyword) list = list.filter((r) => r.skuCode.includes(ctx.query.keyword) || r.spuName.includes(ctx.query.keyword))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'GET', url: '/admin/stock/logs', handler: (ctx) => {
      let list = [...stockLogs].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.skuId) list = list.filter((l) => l.skuId === Number(ctx.query.skuId))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  { method: 'GET', url: '/admin/stock/warning', handler: () => clone(dashboard.warnings) },
  {
    method: 'PUT', url: '/admin/stock/check', handler: (ctx) => {
      const b = ctx.body as { skuId?: number; stock?: number; remark?: string }
      const sku = skuById(b?.skuId || 0)
      if (sku) {
        stockLogs.unshift({ id: nextId(stockLogs), skuId: sku.id, bizSn: 'PD' + Date.now(), changeType: 2, changeCount: (b?.stock || 0) - sku.stock, stockBefore: sku.stock, stockAfter: b?.stock || 0, createTime: fmtDateTime(new Date()) })
        sku.stock = b?.stock || 0
      }
      return null
    }
  },

  // ---- SKU 搜索（秒杀选品） ----
  {
    method: 'GET', url: '/admin/sku/search', handler: (ctx) => {
      const kw = ctx.query.keyword || ''
      return skus
        .filter((s) => s.status === 1 && (s.skuCode.includes(kw) || (spuById(s.spuId)?.name || '').includes(kw)))
        .slice(0, 20)
        .map((s) => ({ id: s.id, skuCode: s.skuCode, spuId: s.spuId, spuName: spuById(s.spuId)?.name, spec: s.spec, price: s.price, stock: s.stock, status: s.status }))
    }
  },

  // ---- 图片上传 ----
  { method: 'POST', url: '/admin/upload/image', handler: () => '/mock-imgs/p1.svg' },

  // ---- 优惠券模板 ----
  {
    method: 'GET', url: '/admin/coupon/page', handler: (ctx) => {
      let list = [...couponTemplates].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.name) list = list.filter((c) => c.name.includes(ctx.query.name))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((c) => c.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/coupon/save', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      if (b?.id) {
        const t = couponTemplates.find((x) => x.id === b.id)
        if (t) Object.assign(t, ctx.body || {})
      } else {
        couponTemplates.unshift({ id: nextId(couponTemplates), receivedCount: 0, status: 1, createTime: fmtDateTime(new Date()), ...(ctx.body as object) } as never)
      }
      return null
    }
  },
  {
    method: 'POST', url: '/admin/coupon/status', handler: (ctx) => {
      const t = couponTemplates.find((x) => x.id === Number(ctx.query.id))
      if (t) t.status = Number(ctx.query.status)
      return null
    }
  },

  // ---- 订单 ----
  {
    method: 'GET', url: '/admin/order/page', handler: (ctx) => {
      let list = [...orders].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.orderSn) list = list.filter((o) => o.orderSn.includes(ctx.query.orderSn))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((o) => o.status === Number(ctx.query.status))
      const rows = list.map((o) => ({
        order: clone(o),
        items: orderItems.filter((i) => i.orderId === o.id).map((i) => clone(i))
      }))
      return paginate(rows, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/order/deliver', handler: (ctx) => {
      const o = orders.find((x) => x.id === Number(ctx.query.orderId))
      if (o && o.status === 1) {
        o.status = 2
        o.deliveryCompany = ctx.query.company
        o.deliverySn = ctx.query.sn
        o.deliveryTime = fmtDateTime(new Date())
      }
      return null
    }
  },

  // ---- 退款 ----
  {
    method: 'GET', url: '/admin/refund/page', handler: (ctx) => {
      let list = [...refunds].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.orderSn) list = list.filter((r) => r.orderSn.includes(ctx.query.orderSn))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((r) => r.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/refund/:id/audit', handler: (ctx) => {
      const r = refunds.find((x) => x.id === Number(ctx.params.id))
      if (r) { r.status = ctx.query.approved === 'true' ? 1 : 4; r.auditBy = ctx.query.auditBy || 'admin'; r.auditTime = fmtDateTime(new Date()) }
      return null
    }
  },
  {
    method: 'POST', url: '/admin/refund/:id/confirm-return', handler: (ctx) => {
      const r = refunds.find((x) => x.id === Number(ctx.params.id))
      if (r) { r.status = 2; r.refundTime = fmtDateTime(new Date()) }
      return null
    }
  },
  { method: 'POST', url: '/admin/refund/:id/retry', handler: () => null },

  // ---- 评价 ----
  {
    method: 'GET', url: '/admin/comment/page', handler: (ctx) => {
      let list = [...comments].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.keyword) list = list.filter((c) => c.spuName.includes(ctx.query.keyword) || (c.content || '').includes(ctx.query.keyword))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((c) => c.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/comment/reply', handler: (ctx) => {
      const c = comments.find((x) => x.id === Number(ctx.query.id))
      if (c) { c.reply = ctx.query.reply || ''; c.replyTime = fmtDateTime(new Date()) }
      return null
    }
  },
  {
    method: 'POST', url: '/admin/comment/status', handler: (ctx) => {
      const c = comments.find((x) => x.id === Number(ctx.query.id))
      if (c) c.status = Number(ctx.query.status)
      return null
    }
  },

  // ---- 秒杀 ----
  {
    method: 'GET', url: '/admin/seckill/session/page', handler: (ctx) => {
      let list = [...seckillSessions].sort((a, b) => b.startTime.localeCompare(a.startTime))
      if (ctx.query.keyword) list = list.filter((s) => s.name.includes(ctx.query.keyword))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((s) => s.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/admin/seckill/session/save', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      if (b?.id) {
        const s = seckillSessions.find((x) => x.id === b.id)
        if (s) Object.assign(s, ctx.body || {})
      } else {
        seckillSessions.unshift({ id: nextId(seckillSessions), status: 1, ...(ctx.body as object) } as never)
      }
      return null
    }
  },
  {
    method: 'POST', url: '/admin/seckill/session/:id/toggle', handler: (ctx) => {
      const s = seckillSessions.find((x) => x.id === Number(ctx.params.id))
      if (s) s.status = Number(ctx.query.status)
      return null
    }
  },
  { method: 'POST', url: '/admin/seckill/session/:id/preheat', handler: () => null },
  {
    method: 'GET', url: '/admin/seckill/product/page', handler: (ctx) => {
      let list = [...seckillProducts]
      if (ctx.query.sessionId) list = list.filter((p) => p.sessionId === Number(ctx.query.sessionId))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((p) => p.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'GET', url: '/admin/seckill/product/list', handler: (ctx) => clone(seckillProducts.filter((p) => p.sessionId === Number(ctx.query.sessionId)))
  },
  {
    method: 'POST', url: '/admin/seckill/product/save', handler: (ctx) => {
      const b = ctx.body as { id?: number }
      if (b?.id) {
        const p = seckillProducts.find((x) => x.id === b.id)
        if (p) Object.assign(p, ctx.body || {})
      } else {
        const b2 = ctx.body as { skuId?: number; sessionId?: number; seckillPrice?: number; seckillStock?: number; limitPerUser?: number }
        const sku = skuById(b2?.skuId || 0)
        seckillProducts.push({
          id: nextId(seckillProducts),
          sessionId: b2?.sessionId || 1,
          spuId: sku?.spuId || 0,
          skuId: b2?.skuId || 0,
          spuName: sku ? spuById(sku.spuId)?.name || '' : '',
          skuCode: sku?.skuCode || '',
          spec: sku?.spec || '',
          pic: sku ? spuById(sku.spuId)?.mainPic || '' : '',
          price: sku?.price || 0,
          seckillPrice: b2?.seckillPrice || 0,
          seckillStock: b2?.seckillStock || 0,
          limitPerUser: b2?.limitPerUser || 1,
          skuStock: sku?.stock || 0,
          status: 1
        })
      }
      return null
    }
  },
  {
    method: 'POST', url: '/admin/seckill/product/:id/toggle', handler: (ctx) => {
      const p = seckillProducts.find((x) => x.id === Number(ctx.params.id))
      if (p) p.status = Number(ctx.query.status)
      return null
    }
  },
  { method: 'POST', url: '/admin/seckill/product/:id/delete', handler: () => null },

  // ---- 网关链路 ----
  { method: 'GET', url: '/common/ping', handler: () => 'pong' },
  { method: 'GET', url: '/common/trace', handler: () => 'mock-trace-id-' + Date.now() },
  // ---- AI 助手（mall-ai /api/ai）----
  { method: 'GET', url: '/ai/config', handler: () => aiMockModels() },
  { method: 'GET', url: '/ai/sessions', handler: () => clone(aiSessions) },
  {
    method: 'GET', url: '/ai/messages', handler: (ctx) =>
      clone(aiMessagesBySession[String(ctx.query.sessionId)] || [])
  },
  {
    method: 'POST', url: '/ai/chat/stream', handler: (ctx) => {
      const body = (ctx.body || {}) as { message?: string; sessionId?: string }
      const reply = aiMockReply(body.message)
      // 追加进会话库：让左侧历史列表 / 刷新后可见新会话（与后端 ai_chat_message 落库语义对齐）
      const sid = body.sessionId || 'mock-ai-' + Date.now()
      const arr = (aiMessagesBySession[sid] ||= [])
      const q = (body.message || '').trim()
      arr.push({ role: 'user', content: q }, { role: 'assistant', content: reply })
      const preview = q.length > 24 ? q.slice(0, 24) + '…' : q
      const found = aiSessions.find((s) => s.sessionId === sid)
      if (found) {
        found.preview = preview
        found.total = arr.length
        found.createTime = fmtDateTime(new Date())
      } else {
        aiSessions.unshift({ sessionId: sid, preview, total: arr.length, createTime: fmtDateTime(new Date()) })
      }
      return { _sse: aiSseFrames(reply) }
    }
  },
  { method: 'POST', url: '/ai/chat', handler: () => null } // 非流式兜底（页面主链路走 /chat/stream）
]
