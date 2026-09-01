import type { MockHandler } from './plugin'
import {
  categories, brands, spus, skus, cart, cartRows, spuById, skuById,
  orders, orderItems, orderById, payments, refunds,
  couponTemplates, myCoupons, seckillSessions, seckillProducts, sessionPhase,
  profile, pointLogs, addresses, favorites, browseHistory, checkinState,
  myComments, likedSpuIds, likeCountMap, banners,
  clone, paginate, fmtDateTime, nextOrderId
} from './db'

// ---------- 验证码 / 认证 ----------

/** 生成 SVG 验证码 data URI（4 位随机数字） */
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

const captchaCode = '8888'

const demoUser = {
  id: 1001,
  username: 'demo',
  nickname: '演示用户',
  avatar: '',
  phone: '13800001234',
  level: 3,
  points: 1280,
  userType: 'MEMBER',
  perms: null
}

function loginResult() {
  return {
    accessToken: 'mock-access-token-' + Date.now(),
    refreshToken: 'mock-refresh-token-' + Date.now(),
    expiresIn: 7200,
    user: demoUser
  }
}

// ---------- 商品 ----------

function productPage(query: Record<string, string>) {
  const page = Number(query.page || 1)
  const size = Number(query.size || 10)
  let list = spus.filter((s) => s.status === 1)
  if (query.categoryId) list = list.filter((s) => String(s.categoryId) === query.categoryId)
  if (query.brandId) list = list.filter((s) => String(s.brandId) === query.brandId)
  if (query.keyword) list = list.filter((s) => s.name.includes(query.keyword) || (s.subtitle || '').includes(query.keyword))
  // 附带最低价（SKU 取 min），供商品卡片直接展示
  const withPrice = list.map((s) => {
    const prices = skus.filter((k) => k.spuId === s.id && k.status === 1).map((k) => k.price)
    return { ...clone(s), price: prices.length ? Math.min(...prices) : 0 }
  })
  return paginate(withPrice, page, size)
}

function spuDetail(id: number) {
  const spu = spuById(id)
  if (!spu) return null
  return {
    spu: clone(spu),
    skuList: skus.filter((s) => s.spuId === id && s.status === 1).map((s) => clone(s)),
    categoryName: categories.flatMap((c) => c.children).find((c) => c.id === spu.categoryId)?.name,
    brandName: brands.find((b) => b.id === spu.brandId)?.name
  }
}

// ---------- 订单 ----------

function orderRow(o: (typeof orders)[number]) {
  return {
    order: clone(o),
    items: orderItems.filter((i) => i.orderId === o.id).map((i) => clone(i))
  }
}

function createPaymentForOrder(o: (typeof orders)[number], payType: number) {
  const payment = {
    id: payments.length + 1,
    paymentSn: 'PAY' + Date.now(),
    orderId: o.id,
    orderSn: o.orderSn,
    memberId: o.memberId,
    payAmount: o.payAmount,
    payType,
    status: 0,
    createTime: fmtDateTime(new Date())
  }
  payments.push(payment)
  return payment
}

// ---------- Handler 注册表 ----------

export const handlers: MockHandler[] = [
  // ---- 首页运营位 ----
  { method: 'GET', url: '/portal/banner', handler: () => clone(banners) },

  // ---- 认证 ----
  { method: 'GET', url: '/auth/captcha', handler: () => ({ uuid: 'mock-captcha-uuid', imgBase64: captchaImg(captchaCode) }) },
  { method: 'GET', url: '/auth/captcha/sms', handler: (ctx) => ({ phone: ctx.query.phone, smsCode: '123456' }) },
  {
    method: 'POST', url: '/auth/login', handler: (ctx) => {
      const b = ctx.body as { username?: string; password?: string }
      if (!b?.username || !b?.password) return { _raw: { code: 401, message: '用户名或密码不能为空' } }
      return loginResult()
    }
  },
  { method: 'POST', url: '/auth/register', handler: () => loginResult() },
  { method: 'POST', url: '/auth/refresh', handler: () => loginResult() },
  { method: 'POST', url: '/auth/logout', handler: () => null },
  { method: 'POST', url: '/auth/forgot-password', handler: () => null },

  // ---- 商品中心 ----
  { method: 'GET', url: '/product/categories', handler: () => clone(categories) },
  { method: 'GET', url: '/product/brands', handler: () => clone(brands) },
  { method: 'GET', url: '/product/list', handler: (ctx) => productPage(ctx.query) },
  { method: 'GET', url: '/product/detail/:id', handler: (ctx) => spuDetail(Number(ctx.params.id)) },
  {
    method: 'GET', url: '/product/hot', handler: (ctx) => {
      const limit = Number(ctx.query.limit || 10)
      // 附带最低价（SKU 取 min），供首页商品卡片直接展示
      return clone(spus.filter((s) => s.status === 1).sort((a, b) => b.sales - a.sales).slice(0, limit)).map((s) => {
        const prices = skus.filter((k) => k.spuId === s.id && k.status === 1).map((k) => k.price)
        return { ...s, price: prices.length ? Math.min(...prices) : 0 }
      })
    }
  },

  // ---- 收藏 ----
  { method: 'POST', url: '/favorite/:spuId', handler: (ctx) => { const id = Number(ctx.params.spuId); if (!favorites.some((f) => f.spuId === id)) favorites.push({ favoriteId: Date.now(), spuId: id, createTime: fmtDateTime(new Date()) }); return null } },
  { method: 'DELETE', url: '/favorite/:spuId', handler: (ctx) => { const id = Number(ctx.params.spuId); const i = favorites.findIndex((f) => f.spuId === id); if (i >= 0) favorites.splice(i, 1); return null } },
  {
    method: 'GET', url: '/favorite/list', handler: (ctx) => {
      const rows = favorites.map((f) => {
        const spu = spuById(f.spuId)
        return { favoriteId: f.favoriteId, spuId: f.spuId, name: spu?.name, subtitle: spu?.subtitle, mainPic: spu?.mainPic, price: skus.find((s) => s.spuId === f.spuId)?.price, createTime: f.createTime }
      })
      return paginate(rows, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  { method: 'GET', url: '/favorite/status/:spuId', handler: (ctx) => favorites.some((f) => f.spuId === Number(ctx.params.spuId)) },

  // ---- 购物车 ----
  { method: 'GET', url: '/cart/list', handler: () => cartRows() },
  {
    method: 'POST', url: '/cart/add', handler: (ctx) => {
      const skuId = Number(ctx.query.skuId)
      const quantity = Number(ctx.query.quantity || 1)
      const row = cart.find((c) => c.skuId === skuId)
      if (row) row.quantity += quantity
      else cart.push({ skuId, quantity, checked: true })
      return null
    }
  },
  {
    method: 'POST', url: '/cart/update', handler: (ctx) => {
      const skuId = Number(ctx.query.skuId)
      const quantity = Number(ctx.query.quantity)
      const row = cart.find((c) => c.skuId === skuId)
      if (row) row.quantity = quantity
      return null
    }
  },
  {
    method: 'POST', url: '/cart/check', handler: (ctx) => {
      const skuIds = (Array.isArray(ctx.body) ? ctx.body : []).map(Number)
      const checked = ctx.query.checked === 'true'
      cart.forEach((c) => { if (skuIds.includes(c.skuId)) c.checked = checked })
      return null
    }
  },
  {
    method: 'DELETE', url: '/cart/remove', handler: (ctx) => {
      const skuIds = String(ctx.query.skuIds || '').split(',').map(Number)
      for (let i = cart.length - 1; i >= 0; i--) if (skuIds.includes(cart[i].skuId)) cart.splice(i, 1)
      return null
    }
  },
  { method: 'GET', url: '/cart/count', handler: () => cart.reduce((n, c) => n + c.quantity, 0) },

  // ---- 结算预览 ----
  {
    method: 'GET', url: '/checkout/preview', handler: () => {
      const items = cartRows().filter((c) => c.checked)
      const totalAmount = items.reduce((n, c) => n + (c.subtotal || 0), 0)
      const availableCoupons = myCoupons
        .filter((c) => c.status === 0 && totalAmount >= c.threshold)
        .map((c) => ({ couponUserId: c.id, couponId: c.couponId, name: c.name, type: c.type, amount: c.amount, threshold: c.threshold, discountAmount: c.amount }))
      return { items, totalAmount, availableCoupons }
    }
  },

  // ---- 订单 ----
  {
    method: 'POST', url: '/order/create', handler: (ctx) => {
      const b = ctx.body as { requestId?: string; receiverName?: string; receiverPhone?: string; receiverAddress?: string; remark?: string; couponUserId?: number }
      const checkedItems = cartRows().filter((c) => c.checked)
      const total = checkedItems.reduce((n, c) => n + (c.subtotal || 0), 0)
      const coupon = myCoupons.find((c) => c.id === b.couponUserId)
      const discount = coupon && total >= coupon.threshold ? coupon.amount : 0
      const order = {
        id: nextOrderId(),
        orderSn: 'M' + Date.now(),
        requestId: b.requestId || 'req-mock',
        memberId: 1001,
        totalAmount: total,
        freightAmount: 0,
        couponAmount: discount,
        discountAmount: discount,
        payAmount: total - discount,
        payType: 1,
        status: 0,
        receiverName: b.receiverName || '张伟',
        receiverPhone: b.receiverPhone || '13800001234',
        receiverAddress: b.receiverAddress || '广东省深圳市南山区科技园南路 1 号',
        remark: b.remark,
        createTime: fmtDateTime(new Date())
      }
      orders.unshift(order)
      checkedItems.forEach((c, i) => {
        orderItems.unshift({
          id: orderItems.length + 1, orderId: order.id, orderSn: order.orderSn,
          spuId: c.spuId || 0, spuName: c.spuName || '', skuId: c.skuId, skuCode: c.skuCode || '',
          spec: c.spec, pic: c.pic, price: c.price || 0, quantity: c.quantity, subtotal: c.subtotal || 0
        })
      })
      if (coupon) { coupon.status = 1; coupon.useTime = fmtDateTime(new Date()); coupon.orderSn = order.orderSn }
      // 清空已勾选购物车项
      for (let i = cart.length - 1; i >= 0; i--) if (cart[i].checked) cart.splice(i, 1)
      return order
    }
  },
  { method: 'POST', url: '/order/:orderSn/cancel', handler: (ctx) => { const o = orders.find((x) => x.orderSn === ctx.params.orderSn); if (o) { o.status = 4; o.cancelBy = 'USER'; o.cancelReason = '用户取消' } return null } },
  { method: 'POST', url: '/order/:orderSn/confirm-receive', handler: (ctx) => { const o = orders.find((x) => x.orderSn === ctx.params.orderSn); if (o) { o.status = 3; o.receiveTime = fmtDateTime(new Date()); o.finishTime = fmtDateTime(new Date()) } return null } },
  {
    method: 'POST', url: '/order/:orderSn/pay', handler: (ctx) => {
      const o = orders.find((x) => x.orderSn === ctx.params.orderSn)
      if (!o) return null
      return createPaymentForOrder(o, Number(ctx.query.payType || 1))
    }
  },
  {
    method: 'GET', url: '/order/:orderSn', handler: (ctx) => {
      const o = orders.find((x) => x.orderSn === ctx.params.orderSn)
      if (!o) return null
      return {
        order: clone(o),
        items: orderItems.filter((i) => i.orderId === o.id).map((i) => clone(i)),
        statusLogs: [
          { id: 1, orderId: o.id, fromStatus: -1, toStatus: 0, operator: 'SYSTEM', remark: '订单创建', createTime: o.createTime },
          ...(o.payTime ? [{ id: 2, orderId: o.id, fromStatus: 0, toStatus: 1, operator: 'SYSTEM', remark: '支付成功', createTime: o.payTime }] : []),
          ...(o.deliveryTime ? [{ id: 3, orderId: o.id, fromStatus: 1, toStatus: 2, operator: 'admin', remark: '商家发货', createTime: o.deliveryTime }] : []),
          ...(o.receiveTime ? [{ id: 4, orderId: o.id, fromStatus: 2, toStatus: 3, operator: 'USER', remark: '确认收货', createTime: o.receiveTime }] : []),
          ...(o.cancelBy ? [{ id: 5, orderId: o.id, fromStatus: 0, toStatus: 4, operator: o.cancelBy, remark: o.cancelReason || '取消订单', createTime: fmtDateTime(new Date()) }] : [])
        ]
      }
    }
  },
  {
    method: 'GET', url: '/order/list', handler: (ctx) => {
      let list = [...orders].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((o) => o.status === Number(ctx.query.status))
      return paginate(list.map(orderRow), Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },

  // ---- 支付 / 退款 ----
  {
    method: 'POST', url: '/payment/:paymentSn/mock-callback', handler: (ctx) => {
      const p = payments.find((x) => x.paymentSn === ctx.params.paymentSn)
      if (!p) return null
      p.status = 2
      p.tradeNo = 'T' + Date.now()
      p.notifyTime = fmtDateTime(new Date())
      const o = orderById(p.orderId)
      if (o) { o.status = 1; o.payTime = fmtDateTime(new Date()); o.payType = p.payType }
      return clone(p)
    }
  },
  {
    method: 'GET', url: '/payment/query', handler: (ctx) => {
      const o = orders.find((x) => x.orderSn === ctx.query.orderSn)
      if (!o) return null
      let p = payments.find((x) => x.orderSn === o.orderSn)
      if (!p) p = createPaymentForOrder(o, o.payType)
      // 模拟已支付（前端结果页轮询体验）
      if (p.status === 0 && o.status === 0) { p.status = 2; o.status = 1; o.payTime = fmtDateTime(new Date()) }
      return clone(p)
    }
  },
  {
    method: 'POST', url: '/payment/refund/apply', handler: (ctx) => {
      const b = ctx.body as { orderSn?: string; reason?: string; refundType?: number }
      const o = orders.find((x) => x.orderSn === b.orderSn)
      if (!o) return null
      refunds.unshift({
        id: refunds.length + 1,
        refundSn: 'RF' + Date.now(),
        orderId: o.id,
        orderSn: o.orderSn,
        paymentSn: payments.find((p) => p.orderSn === o.orderSn)?.paymentSn || 'PAYMOCK',
        memberId: 1001,
        refundAmount: o.payAmount,
        reason: b.reason || '不想要了',
        refundType: b.refundType || 1,
        status: 0,
        applyTime: fmtDateTime(new Date()),
        createTime: fmtDateTime(new Date())
      })
      return null
    }
  },
  {
    method: 'GET', url: '/payment/refund/list', handler: (ctx) => {
      let list = [...refunds].sort((a, b) => b.createTime.localeCompare(a.createTime))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((r) => r.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },

  // ---- 优惠券 ----
  {
    method: 'GET', url: '/coupon/center', handler: (ctx) => {
      const now = Date.now()
      const list = couponTemplates
        .filter((c) => c.status === 1 && new Date(c.useEndTime.replace(' ', 'T')).getTime() > now && c.receivedCount < c.totalCount)
        .map((c) => {
          const myReceived = myCoupons.filter((m) => m.couponId === c.id).length
          return { ...clone(c), remaining: c.totalCount - c.receivedCount, myReceived, receivable: myReceived < c.perLimit }
        })
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },
  {
    method: 'POST', url: '/coupon/receive', handler: (ctx) => {
      const t = couponTemplates.find((c) => c.id === Number(ctx.query.couponId))
      if (!t) return null
      t.receivedCount += 1
      myCoupons.unshift({
        id: myCoupons.length + 1, couponId: t.id, name: t.name, type: t.type, amount: t.amount, threshold: t.threshold,
        status: 0, receiveTime: fmtDateTime(new Date()), expireTime: t.useEndTime
      })
      return null
    }
  },
  {
    method: 'GET', url: '/coupon/mine', handler: (ctx) => {
      let list = [...myCoupons].sort((a, b) => b.receiveTime.localeCompare(a.receiveTime))
      if (ctx.query.status !== undefined && ctx.query.status !== '') list = list.filter((c) => c.status === Number(ctx.query.status))
      return paginate(list, Number(ctx.query.page || 1), Number(ctx.query.size || 10))
    }
  },

  // ---- 秒杀 ----
  {
    method: 'GET', url: '/seckill/sessions', handler: () => seckillSessions.map((s) => ({ id: s.id, name: s.name, startTime: s.startTime, endTime: s.endTime, status: s.status, phase: sessionPhase(s) }))
  },
  {
    method: 'GET', url: '/seckill/sessions/:id/products', handler: (ctx) => {
      const sessionId = Number(ctx.params.id)
      return clone(seckillProducts.filter((p) => p.sessionId === sessionId && p.status === 1))
    }
  },
  {
    method: 'GET', url: '/seckill/sessions/:id/rank', handler: (ctx) => {
      const sessionId = Number(ctx.params.id)
      const topN = Number(ctx.query.topN || 10)
      return seckillProducts
        .filter((p) => p.sessionId === sessionId)
        .map((p) => ({ skuId: p.skuId, sales: p.seckillStock - p.remainStock + 12, spuName: p.spuName, pic: p.pic, price: p.price }))
        .sort((a, b) => b.sales - a.sales)
        .slice(0, topN)
    }
  },
  { method: 'GET', url: '/seckill/token', handler: () => ({ token: 'mock-seckill-token-' + Date.now() }) },
  {
    method: 'POST', url: '/seckill/submit', handler: () => ({
      queued: true,
      seckillProductId: 3,
      requestId: 'req-seckill-' + Date.now(),
      tip: '已进入排队，请稍候查看结果'
    })
  },
  {
    method: 'GET', url: '/seckill/result', handler: () => {
      const o = orders[0]
      return { status: 1, orderSn: o ? o.orderSn : 'M' + Date.now(), tip: '秒杀成功，订单已生成' }
    }
  },

  // ---- 签到 / 商品运营 ----
  {
    method: 'POST', url: '/member/stats/checkin', handler: () => {
      if (!checkinState.signedToday) {
        checkinState.signedToday = true
        checkinState.monthDays += 1
        checkinState.streakDays += 1
      }
      return clone(checkinState)
    }
  },
  { method: 'GET', url: '/member/stats/checkin/status', handler: () => clone(checkinState) },
  {
    method: 'GET', url: '/product/stats/sales-rank', handler: (ctx) => {
      const topN = Number(ctx.query.topN || 10)
      return spus
        .filter((s) => s.status === 1)
        .map((s) => ({ skuId: skus.find((k) => k.spuId === s.id)?.id || 0, sales: s.sales, spuId: s.id, spuName: s.name, pic: s.mainPic, price: skus.find((k) => k.spuId === s.id)?.price }))
        .sort((a, b) => b.sales - a.sales)
        .slice(0, topN)
    }
  },
  { method: 'POST', url: '/product/stats/view', handler: () => null },
  { method: 'POST', url: '/product/stats/like/:spuId', handler: (ctx) => { const id = Number(ctx.params.spuId); likedSpuIds.add(id); likeCountMap.set(id, (likeCountMap.get(id) || 0) + 1); return true } },
  { method: 'DELETE', url: '/product/stats/like/:spuId', handler: (ctx) => { const id = Number(ctx.params.spuId); likedSpuIds.delete(id); likeCountMap.set(id, Math.max(0, (likeCountMap.get(id) || 0) - 1)); return null } },
  { method: 'GET', url: '/product/stats/like/status/:spuId', handler: (ctx) => likedSpuIds.has(Number(ctx.params.spuId)) },
  { method: 'GET', url: '/product/stats/like/count/:spuId', handler: (ctx) => likeCountMap.get(Number(ctx.params.spuId)) || 0 },
  {
    method: 'GET', url: '/product/stats/history', handler: () => browseHistory.map((h) => {
      const spu = spuById(h.spuId)
      return { spuId: h.spuId, viewTime: h.viewTime, spuName: spu?.name, mainPic: spu?.mainPic }
    })
  },

  // ---- 会员 ----
  { method: 'GET', url: '/member/profile', handler: () => clone(profile) },
  {
    method: 'PUT', url: '/member/profile', handler: (ctx) => {
      Object.assign(profile, ctx.body || {})
      return null
    }
  },
  {
    method: 'GET', url: '/member/points', handler: () => ({
      memberId: profile.id,
      points: profile.points,
      level: profile.level,
      levelInfo: { level: 3, name: '黄金会员', discount: 98, pointsRate: 1, freeShipping: true }
    })
  },
  { method: 'GET', url: '/member/point-logs', handler: (ctx) => paginate(pointLogs, Number(ctx.query.page || 1), Number(ctx.query.size || 10)) },
  { method: 'GET', url: '/member/address', handler: () => clone(addresses) },
  {
    method: 'POST', url: '/member/address', handler: (ctx) => {
      const b = ctx.body as Record<string, unknown>
      const addr = { id: addresses.length + 1, ...b, defaultFlag: addresses.length ? 0 : 1 }
      addresses.push(addr as never)
      return addr
    }
  },
  {
    method: 'PUT', url: '/member/address/:id', handler: (ctx) => {
      const a = addresses.find((x) => x.id === Number(ctx.params.id))
      if (a) Object.assign(a, ctx.body || {})
      return null
    }
  },
  { method: 'DELETE', url: '/member/address/:id', handler: (ctx) => { const i = addresses.findIndex((x) => x.id === Number(ctx.params.id)); if (i >= 0) addresses.splice(i, 1); return null } },
  {
    method: 'PUT', url: '/member/address/:id/default', handler: (ctx) => {
      addresses.forEach((a) => { a.defaultFlag = a.id === Number(ctx.params.id) ? 1 : 0 })
      return null
    }
  },

  // ---- 搜索 ----
  {
    method: 'GET', url: '/search', handler: (ctx) => {
      const page = Number(ctx.query.page || 1)
      const size = Number(ctx.query.size || 10)
      let list = spus.filter((s) => s.status === 1)
      if (ctx.query.categoryId) list = list.filter((s) => String(s.categoryId) === ctx.query.categoryId)
      if (ctx.query.keyword) list = list.filter((s) => s.name.includes(ctx.query.keyword) || (s.subtitle || '').includes(ctx.query.keyword))
      const total = list.length
      const records = list.slice((page - 1) * size, page * size).map((s) => ({
        spuId: s.id,
        name: s.name,
        subtitle: s.subtitle,
        pic: s.mainPic,
        price: skus.find((k) => k.spuId === s.id)?.price,
        sales: s.sales,
        categoryName: categories.flatMap((c) => c.children).find((c) => c.id === s.categoryId)?.name,
        brandName: brands.find((b) => b.id === s.brandId)?.name,
        highlightName: ctx.query.keyword ? s.name.replace(ctx.query.keyword, `<em>${ctx.query.keyword}</em>`) : undefined
      }))
      return { total, records }
    }
  },
  {
    method: 'GET', url: '/search/suggest', handler: (ctx) => {
      const prefix = ctx.query.prefix || ''
      return spus.filter((s) => s.name.includes(prefix)).slice(0, 8).map((s) => s.name)
    }
  },

  // ---- 评论 ----
  {
    method: 'POST', url: '/comment', handler: (ctx) => {
      const b = ctx.body as { orderItemId?: number; rating?: number; content?: string; pics?: string[] }
      const item = orderItems.find((i) => i.id === b.orderItemId)
      myComments.unshift({
        id: myComments.length + 1,
        orderItemId: b.orderItemId || 0,
        spuId: item?.spuId || 0,
        spuName: item?.spuName || '',
        skuSpec: item?.spec || '',
        pic: item?.pic || '',
        rating: b.rating || 5,
        content: b.content || '',
        pics: (b.pics || []).join(','),
        reply: '',
        status: 1,
        createTime: fmtDateTime(new Date())
      })
      if (item) item.commented = 1
      return null
    }
  },
  { method: 'GET', url: '/comment/mine', handler: (ctx) => paginate(myComments, Number(ctx.query.page || 1), Number(ctx.query.size || 10)) },

  // ---- 网关链路 ----
  { method: 'GET', url: '/common/ping', handler: () => 'pong' },
  { method: 'GET', url: '/common/trace', handler: () => 'mock-trace-id-' + Date.now() }
]
