// ---------- Mock 演示数据（内存态；刷新页面后重置） ----------
// 数据语义与 sql/mall.sql 业务对齐：商品/分类/品牌/订单/优惠券/秒杀等。

/** 时间工具：生成 yyyy-MM-dd HH:mm:ss 与 yyyy-MM-dd */
function pad(n: number) {
  return n < 10 ? '0' + n : String(n)
}
export function fmtDateTime(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
export function fmtDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
export function hoursAgo(h: number): Date {
  return new Date(Date.now() - h * 3600_000)
}
export function daysAgo(n: number): Date {
  return new Date(Date.now() - n * 86400_000)
}

// ---------- 分类 / 品牌 ----------

export const categories = [
  {
    id: 1, parentId: 0, name: '手机数码', level: 1, icon: 'phone-o', sort: 1, status: 1,
    children: [
      { id: 11, parentId: 1, name: '手机', level: 2, icon: '', sort: 1, status: 1, children: [] },
      { id: 12, parentId: 1, name: '平板电脑', level: 2, icon: '', sort: 2, status: 1, children: [] },
      { id: 13, parentId: 1, name: '耳机音箱', level: 2, icon: '', sort: 3, status: 1, children: [] }
    ]
  },
  {
    id: 2, parentId: 0, name: '电脑办公', level: 1, icon: 'computer-o', sort: 2, status: 1,
    children: [
      { id: 21, parentId: 2, name: '笔记本电脑', level: 2, icon: '', sort: 1, status: 1, children: [] },
      { id: 22, parentId: 2, name: '显示器', level: 2, icon: '', sort: 2, status: 1, children: [] },
      { id: 23, parentId: 2, name: '键鼠外设', level: 2, icon: '', sort: 3, status: 1, children: [] }
    ]
  },
  {
    id: 3, parentId: 0, name: '家用电器', level: 1, icon: 'tv-o', sort: 3, status: 1,
    children: [
      { id: 31, parentId: 3, name: '电视', level: 2, icon: '', sort: 1, status: 1, children: [] },
      { id: 32, parentId: 3, name: '冰箱', level: 2, icon: '', sort: 2, status: 1, children: [] },
      { id: 33, parentId: 3, name: '厨房电器', level: 2, icon: '', sort: 3, status: 1, children: [] }
    ]
  }
]

export const brands = [
  { id: 1, name: 'Apple', logo: '' },
  { id: 2, name: '华为', logo: '' },
  { id: 3, name: '小米', logo: '' },
  { id: 4, name: '索尼', logo: '' },
  { id: 5, name: '戴尔', logo: '' },
  { id: 6, name: '漫步者', logo: '' },
  { id: 7, name: '海信', logo: '' },
  { id: 8, name: '海尔', logo: '' },
  { id: 9, name: '美的', logo: '' }
]

// ---------- 首页 Banner（运营位） ----------

export interface MockBanner {
  id: number
  title: string
  subtitle: string
  color1: string
  color2: string
  emoji: string
  link: string
}

export const banners: MockBanner[] = [
  { id: 1, title: '新品首发', subtitle: 'iPhone 15 Pro · 钛金属旗舰 · A17 Pro 芯片', color1: '#1e3a8a', color2: '#3b82f6', emoji: '📱', link: '/product/list?categoryId=11' },
  { id: 2, title: '限时秒杀', subtitle: '整点开抢 · 全场低至 5 折', color1: '#9f1239', color2: '#f43f5e', emoji: '⚡', link: '/seckill' },
  { id: 3, title: '电脑办公节', subtitle: '戴尔 XPS 13 · 轻薄本狂欢', color1: '#134e4a', color2: '#14b8a6', emoji: '💻', link: '/product/list?categoryId=21' },
  { id: 4, title: '家电焕新', subtitle: 'Mini LED 电视 · 领券立减', color1: '#713f12', color2: '#f59e0b', emoji: '📺', link: '/product/list?categoryId=31' }
]

// ---------- 商品（SPU / SKU） ----------

export interface MockSku {
  id: number
  spuId: number
  skuCode: string
  spec: string
  price: number
  stock: number
  lowStock: number
  status: number
  version: number
}

export interface MockSpu {
  id: number
  spuCode: string
  categoryId: number
  brandId: number
  name: string
  subtitle: string
  mainPic: string
  pics: string
  unit: string
  detail: string
  status: number
  sort: number
  sales: number
  createTime: string
}

export const spus: MockSpu[] = [
  { id: 1, spuCode: 'SPU202608270001', categoryId: 11, brandId: 1, name: 'Apple iPhone 15 Pro', subtitle: 'A17 Pro 芯片 · 钛金属机身 · 4800 万像素三摄', mainPic: '/mock-imgs/p1.svg', pics: '/mock-imgs/p1.svg,/mock-imgs/p2.svg,/mock-imgs/p3.svg', unit: '台', detail: '6.1 英寸超视网膜 XDR 显示屏，A17 Pro 芯片，钛金属设计，支持 USB-C 接口。', status: 1, sort: 1, sales: 3286, createTime: fmtDateTime(daysAgo(120)) },
  { id: 2, spuCode: 'SPU202608270002', categoryId: 11, brandId: 2, name: '华为 Mate 60 Pro', subtitle: '卫星通话 · 昆仑玻璃 · 鸿蒙 4.0', mainPic: '/mock-imgs/p2.svg', pics: '/mock-imgs/p2.svg,/mock-imgs/p3.svg,/mock-imgs/p4.svg', unit: '台', detail: '支持卫星通话与北斗消息，昆仑玻璃二代，5000mAh 大电池，88W 超级快充。', status: 1, sort: 2, sales: 5241, createTime: fmtDateTime(daysAgo(115)) },
  { id: 3, spuCode: 'SPU202608270003', categoryId: 11, brandId: 3, name: '小米 14', subtitle: '骁龙 8 Gen3 · 徕卡光学镜头 · 小尺寸旗舰', mainPic: '/mock-imgs/p3.svg', pics: '/mock-imgs/p3.svg,/mock-imgs/p4.svg,/mock-imgs/p5.svg', unit: '台', detail: '6.36 英寸小尺寸旗舰，徕卡 Summilux 光学镜头，澎湃 OS，90W 有线快充。', status: 1, sort: 3, sales: 8932, createTime: fmtDateTime(daysAgo(110)) },
  { id: 4, spuCode: 'SPU202608270004', categoryId: 12, brandId: 1, name: 'Apple iPad Air', subtitle: 'M2 芯片 · 11 英寸 Liquid 视网膜屏 · 支持 Apple Pencil Pro', mainPic: '/mock-imgs/p4.svg', pics: '/mock-imgs/p4.svg,/mock-imgs/p5.svg,/mock-imgs/p6.svg', unit: '台', detail: 'M2 芯片性能强劲，11 英寸 Liquid 视网膜显示屏，横向前置摄像头，支持 Apple Pencil Pro。', status: 1, sort: 4, sales: 2150, createTime: fmtDateTime(daysAgo(95)) },
  { id: 5, spuCode: 'SPU202608270005', categoryId: 12, brandId: 2, name: '华为 MatePad Pro 13.2', subtitle: '13.2 英寸 OLED 柔性屏 · 星闪连接 · 轻薄旗舰平板', mainPic: '/mock-imgs/p5.svg', pics: '/mock-imgs/p5.svg,/mock-imgs/p6.svg,/mock-imgs/p7.svg', unit: '台', detail: '13.2 英寸 144Hz OLED 屏幕，星闪技术，鸿蒙智慧互联，10100mAh 电池。', status: 1, sort: 5, sales: 1876, createTime: fmtDateTime(daysAgo(90)) },
  { id: 6, spuCode: 'SPU202608270006', categoryId: 13, brandId: 4, name: '索尼 WH-1000XM5 降噪耳机', subtitle: '8 麦克风降噪 · 30 小时续航 · 高清无线音质', mainPic: '/mock-imgs/p6.svg', pics: '/mock-imgs/p6.svg,/mock-imgs/p7.svg,/mock-imgs/p8.svg', unit: '副', detail: '新一代降噪旗舰，8 麦克风系统，30 小时续航，支持 LDAC 高清无线传输。', status: 1, sort: 6, sales: 6543, createTime: fmtDateTime(daysAgo(85)) },
  { id: 7, spuCode: 'SPU202608270007', categoryId: 13, brandId: 6, name: '漫步者 NeoBuds Pro 2', subtitle: '48dB 深度降噪 · Hi-Res 认证 · 空间音频', mainPic: '/mock-imgs/p7.svg', pics: '/mock-imgs/p7.svg,/mock-imgs/p8.svg,/mock-imgs/p9.svg', unit: '副', detail: '48dB 自适应主动降噪，Hi-Res Wireless 金标认证，支持空间音频与低延迟游戏模式。', status: 1, sort: 7, sales: 12780, createTime: fmtDateTime(daysAgo(80)) },
  { id: 8, spuCode: 'SPU202608270008', categoryId: 21, brandId: 5, name: '戴尔 XPS 13 笔记本', subtitle: '13.4 英寸 3K 触控屏 · 酷睿 Ultra 7 · 轻至 1.17kg', mainPic: '/mock-imgs/p8.svg', pics: '/mock-imgs/p8.svg,/mock-imgs/p9.svg,/mock-imgs/p10.svg', unit: '台', detail: '13.4 英寸 3K 触控屏，英特尔酷睿 Ultra 7 处理器，CNC 铝机身，轻至 1.17kg。', status: 1, sort: 8, sales: 982, createTime: fmtDateTime(daysAgo(75)) },
  { id: 9, spuCode: 'SPU202608270009', categoryId: 21, brandId: 3, name: '小米笔记本 Pro 14', subtitle: '2.8K 120Hz 高刷屏 · 标压酷睿 Ultra · 全金属机身', mainPic: '/mock-imgs/p9.svg', pics: '/mock-imgs/p9.svg,/mock-imgs/p10.svg,/mock-imgs/p11.svg', unit: '台', detail: '2.8K 120Hz 高刷屏，标压酷睿 Ultra 5，32GB 大内存，全金属轻薄机身。', status: 1, sort: 9, sales: 2345, createTime: fmtDateTime(daysAgo(70)) },
  { id: 10, spuCode: 'SPU202608270010', categoryId: 22, brandId: 5, name: '戴尔 UltraSharp U2723QE 显示器', subtitle: '27 英寸 4K IPS Black · 98% DCI-P3 · 90W 反向充电', mainPic: '/mock-imgs/p10.svg', pics: '/mock-imgs/p10.svg,/mock-imgs/p11.svg,/mock-imgs/p12.svg', unit: '台', detail: '27 英寸 4K IPS Black 面板，98% DCI-P3 色域，USB-C 90W 反向充电，硬件防蓝光。', status: 1, sort: 10, sales: 1120, createTime: fmtDateTime(daysAgo(65)) },
  { id: 11, spuCode: 'SPU202608270011', categoryId: 23, brandId: 3, name: '小米无线鼠标 MX Master 同款手感', subtitle: '静音按键 · 三模连接 · 人体工学设计', mainPic: '/mock-imgs/p11.svg', pics: '/mock-imgs/p11.svg,/mock-imgs/p12.svg,/mock-imgs/p13.svg', unit: '个', detail: '静音微动按键，支持蓝牙/2.4G/有线三模连接，人体工学造型，2400DPI。', status: 1, sort: 11, sales: 15678, createTime: fmtDateTime(daysAgo(60)) },
  { id: 12, spuCode: 'SPU202608270012', categoryId: 23, brandId: 3, name: '小米机械键盘 87 键', subtitle: 'Gasket 结构 · 全键热插拔 · 三模连接', mainPic: '/mock-imgs/p12.svg', pics: '/mock-imgs/p12.svg,/mock-imgs/p13.svg,/mock-imgs/p14.svg', unit: '个', detail: 'Gasket 结构手感软弹，全键热插拔，支持有线/蓝牙/2.4G 三模，RGB 背光。', status: 1, sort: 12, sales: 4321, createTime: fmtDateTime(daysAgo(55)) },
  { id: 13, spuCode: 'SPU202608270013', categoryId: 31, brandId: 7, name: '海信 65E7N 65 英寸电视', subtitle: 'Mini LED · 144Hz 高刷 · ULED 画质引擎', mainPic: '/mock-imgs/p13.svg', pics: '/mock-imgs/p13.svg,/mock-imgs/p14.svg,/mock-imgs/p15.svg', unit: '台', detail: '65 英寸 Mini LED 背光，144Hz 原生高刷，ULED 画质引擎，4GB+64GB 大存储。', status: 1, sort: 13, sales: 876, createTime: fmtDateTime(daysAgo(50)) },
  { id: 14, spuCode: 'SPU202608270014', categoryId: 32, brandId: 8, name: '海尔 501L 十字对开门冰箱', subtitle: '全空间保鲜 · 一级能效 · 阻氧干湿分储', mainPic: '/mock-imgs/p14.svg', pics: '/mock-imgs/p14.svg,/mock-imgs/p15.svg,/mock-imgs/p1.svg', unit: '台', detail: '501L 大容量十字对开门，全空间保鲜科技，一级能效，阻氧干湿分储抽屉。', status: 1, sort: 14, sales: 654, createTime: fmtDateTime(daysAgo(45)) },
  { id: 15, spuCode: 'SPU202608270015', categoryId: 33, brandId: 9, name: '美的微波炉 M1-L213B', subtitle: '21L 家用 · 智能解冻 · 平板加热', mainPic: '/mock-imgs/p15.svg', pics: '/mock-imgs/p15.svg,/mock-imgs/p1.svg,/mock-imgs/p2.svg', unit: '台', detail: '21L 家用平板微波炉，智能解冻，五档火力，易清洁内胆，一级能效。', status: 1, sort: 15, sales: 3209, createTime: fmtDateTime(daysAgo(40)) }
]

export const skus: MockSku[] = [
  { id: 101, spuId: 1, skuCode: 'SKU20260827101', spec: '256GB 原色钛金属', price: 7999, stock: 320, lowStock: 20, status: 1, version: 1 },
  { id: 102, spuId: 1, skuCode: 'SKU20260827102', spec: '512GB 白色钛金属', price: 9299, stock: 180, lowStock: 20, status: 1, version: 1 },
  { id: 103, spuId: 2, skuCode: 'SKU20260827201', spec: '12GB+256GB 雅丹黑', price: 6999, stock: 260, lowStock: 15, status: 1, version: 1 },
  { id: 104, spuId: 2, skuCode: 'SKU20260827202', spec: '12GB+512GB 白沙银', price: 7999, stock: 120, lowStock: 15, status: 1, version: 1 },
  { id: 105, spuId: 3, skuCode: 'SKU20260827301', spec: '12GB+256GB 黑色', price: 3999, stock: 500, lowStock: 30, status: 1, version: 1 },
  { id: 106, spuId: 3, skuCode: 'SKU20260827302', spec: '16GB+512GB 雪山粉', price: 4599, stock: 300, lowStock: 30, status: 1, version: 1 },
  { id: 107, spuId: 4, skuCode: 'SKU20260827401', spec: '128GB 深空灰', price: 4799, stock: 150, lowStock: 10, status: 1, version: 1 },
  { id: 108, spuId: 5, skuCode: 'SKU20260827501', spec: '12GB+512GB 曜金黑', price: 2399, stock: 400, lowStock: 20, status: 1, version: 1 },
  { id: 109, spuId: 6, skuCode: 'SKU20260827601', spec: '黑色', price: 2499, stock: 380, lowStock: 20, status: 1, version: 1 },
  { id: 110, spuId: 7, skuCode: 'SKU20260827701', spec: '白色', price: 399, stock: 1000, lowStock: 50, status: 1, version: 1 },
  { id: 111, spuId: 8, skuCode: 'SKU20260827801', spec: '酷睿 Ultra 7 / 16GB / 512GB', price: 10999, stock: 80, lowStock: 8, status: 1, version: 1 },
  { id: 112, spuId: 9, skuCode: 'SKU20260827901', spec: '酷睿 Ultra 5 / 32GB / 1TB', price: 5299, stock: 200, lowStock: 15, status: 1, version: 1 },
  { id: 113, spuId: 10, skuCode: 'SKU20260827101', spec: '27 英寸 4K', price: 3299, stock: 160, lowStock: 10, status: 1, version: 1 },
  { id: 114, spuId: 11, skuCode: 'SKU20260827112', spec: '深灰色', price: 699, stock: 800, lowStock: 40, status: 1, version: 1 },
  { id: 115, spuId: 12, skuCode: 'SKU20260827121', spec: '87 键 黑灰', price: 299, stock: 600, lowStock: 30, status: 1, version: 1 },
  { id: 116, spuId: 13, skuCode: 'SKU20260827131', spec: '65 英寸 Mini LED', price: 3999, stock: 90, lowStock: 8, status: 1, version: 1 },
  { id: 117, spuId: 14, skuCode: 'SKU20260827141', spec: '501L 星蕴灰', price: 3499, stock: 70, lowStock: 6, status: 1, version: 1 },
  { id: 118, spuId: 15, skuCode: 'SKU20260827151', spec: '21L 白色', price: 599, stock: 450, lowStock: 25, status: 1, version: 1 }
]

export function spuById(id: number) {
  return spus.find((s) => s.id === id)
}
export function skuById(id: number) {
  return skus.find((s) => s.id === id)
}

// ---------- 购物车（内存数组；刷新重置） ----------

export interface MockCartRow {
  skuId: number
  quantity: number
  checked: boolean
}
export const cart: MockCartRow[] = [
  { skuId: 105, quantity: 1, checked: true },
  { skuId: 110, quantity: 2, checked: true },
  { skuId: 113, quantity: 1, checked: false }
]

/** 购物车行 → 前端 CartRow（合并商品快照） */
export function cartRows() {
  return cart.map((r) => {
    const sku = skuById(r.skuId)
    const spu = sku ? spuById(sku.spuId) : undefined
    return {
      skuId: r.skuId,
      quantity: r.quantity,
      checked: r.checked,
      invalid: false,
      skuCode: sku?.skuCode,
      spuId: spu?.id,
      spuName: spu?.name,
      spec: sku?.spec,
      pic: spu?.mainPic,
      price: sku?.price,
      stock: sku?.stock,
      subtotal: (sku?.price || 0) * r.quantity
    }
  })
}

// ---------- 订单 ----------

export interface MockOrder {
  id: number
  orderSn: string
  requestId: string
  memberId: number
  totalAmount: number
  freightAmount: number
  couponAmount: number
  discountAmount: number
  payAmount: number
  payType: number
  status: number
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
  payTime?: string
  deliveryCompany?: string
  deliverySn?: string
  deliveryTime?: string
  receiveTime?: string
  finishTime?: string
  cancelBy?: string
  cancelReason?: string
  createTime: string
}

export interface MockOrderItem {
  id: number
  orderId: number
  orderSn: string
  spuId: number
  spuName: string
  skuId: number
  skuCode: string
  spec?: string
  pic?: string
  price: number
  quantity: number
  subtotal: number
  commented?: number
}

let orderSeq = 9000
export function nextOrderId() {
  return ++orderSeq
}

function makeOrderSn() {
  const d = new Date()
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${d.getTime()}`
}

export const orders: MockOrder[] = [
  { id: 1, orderSn: '20260827100120001', requestId: 'req-1001', memberId: 1001, totalAmount: 4398, freightAmount: 0, couponAmount: 30, discountAmount: 30, payAmount: 4368, payType: 1, status: 2, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', remark: '工作日白天派送', payTime: fmtDateTime(daysAgo(3)), deliveryCompany: '顺丰速运', deliverySn: 'SF1389123456789', deliveryTime: fmtDateTime(daysAgo(2)), createTime: fmtDateTime(daysAgo(3)) },
  { id: 2, orderSn: '20260828100220002', requestId: 'req-1002', memberId: 1001, totalAmount: 7999, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 7999, payType: 1, status: 1, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(1)), createTime: fmtDateTime(daysAgo(1)) },
  { id: 3, orderSn: '20260829100330003', requestId: 'req-1003', memberId: 1001, totalAmount: 399, freightAmount: 10, couponAmount: 0, discountAmount: 0, payAmount: 409, payType: 1, status: 0, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', createTime: fmtDateTime(hoursAgo(5)) },
  { id: 4, orderSn: '20260825100440004', requestId: 'req-1004', memberId: 1001, totalAmount: 4998, freightAmount: 0, couponAmount: 50, discountAmount: 50, payAmount: 4948, payType: 2, status: 3, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(20)), deliveryCompany: '京东物流', deliverySn: 'JD8890123456', deliveryTime: fmtDateTime(daysAgo(18)), receiveTime: fmtDateTime(daysAgo(15)), finishTime: fmtDateTime(daysAgo(15)), createTime: fmtDateTime(daysAgo(20)) },
  { id: 5, orderSn: '20260822100550005', requestId: 'req-1005', memberId: 1001, totalAmount: 2499, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 2499, payType: 1, status: 4, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', cancelBy: 'USER', cancelReason: '拍错了，重新下单', createTime: fmtDateTime(daysAgo(9)) },
  { id: 6, orderSn: '20260818100660006', requestId: 'req-1006', memberId: 1001, totalAmount: 599, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 599, payType: 1, status: 5, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(12)), deliveryCompany: '顺丰速运', deliverySn: 'SF1389123456000', deliveryTime: fmtDateTime(daysAgo(11)), receiveTime: fmtDateTime(daysAgo(9)), createTime: fmtDateTime(daysAgo(12)) }
]

export const orderItems: MockOrderItem[] = [
  { id: 1, orderId: 1, orderSn: '20260827100120001', spuId: 3, spuName: '小米 14', skuId: 105, skuCode: 'SKU20260827301', spec: '12GB+256GB 黑色', pic: '/mock-imgs/p3.svg', price: 3999, quantity: 1, subtotal: 3999 },
  { id: 2, orderId: 1, orderSn: '20260827100120001', spuId: 7, spuName: '漫步者 NeoBuds Pro 2', skuId: 110, skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, quantity: 1, subtotal: 399 },
  { id: 3, orderId: 2, orderSn: '20260828100220002', spuId: 1, spuName: 'Apple iPhone 15 Pro', skuId: 101, skuCode: 'SKU20260827101', spec: '256GB 原色钛金属', pic: '/mock-imgs/p1.svg', price: 7999, quantity: 1, subtotal: 7999 },
  { id: 4, orderId: 3, orderSn: '20260829100330003', spuId: 7, spuName: '漫步者 NeoBuds Pro 2', skuId: 110, skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, quantity: 1, subtotal: 399 },
  { id: 5, orderId: 4, orderSn: '20260825100440004', spuId: 10, spuName: '戴尔 UltraSharp U2723QE 显示器', skuId: 113, skuCode: 'SKU20260827101', spec: '27 英寸 4K', pic: '/mock-imgs/p10.svg', price: 3299, quantity: 1, subtotal: 3299, commented: 1 },
  { id: 6, orderId: 4, orderSn: '20260825100440004', spuId: 12, spuName: '小米机械键盘 87 键', skuId: 115, skuCode: 'SKU20260827121', spec: '87 键 黑灰', pic: '/mock-imgs/p12.svg', price: 299, quantity: 1, subtotal: 299, commented: 1 },
  { id: 7, orderId: 4, orderSn: '20260825100440004', spuId: 11, spuName: '小米无线鼠标', skuId: 114, skuCode: 'SKU20260827112', spec: '深灰色', pic: '/mock-imgs/p11.svg', price: 699, quantity: 2, subtotal: 1398, commented: 0 },
  { id: 8, orderId: 5, orderSn: '20260822100550005', spuId: 6, spuName: '索尼 WH-1000XM5 降噪耳机', skuId: 109, skuCode: 'SKU20260827601', spec: '黑色', pic: '/mock-imgs/p6.svg', price: 2499, quantity: 1, subtotal: 2499 },
  { id: 9, orderId: 6, orderSn: '20260818100660006', spuId: 15, spuName: '美的微波炉 M1-L213B', skuId: 118, skuCode: 'SKU20260827151', spec: '21L 白色', pic: '/mock-imgs/p15.svg', price: 599, quantity: 1, subtotal: 599 }
]

export function orderById(id: number) {
  return orders.find((o) => o.id === id)
}

// ---------- 支付 / 退款 ----------

export interface MockPayment {
  id: number
  paymentSn: string
  orderId: number
  orderSn: string
  memberId: number
  payAmount: number
  payType: number
  tradeNo?: string
  status: number
  notifyTime?: string
  createTime: string
}

export interface MockRefund {
  id: number
  refundSn: string
  orderId: number
  orderSn: string
  paymentSn: string
  memberId: number
  refundAmount: number
  reason?: string
  refundType: number
  returnCompany?: string
  returnSn?: string
  status: number
  auditBy?: string
  auditTime?: string
  applyTime: string
  refundTime?: string
  createTime: string
}

export const payments: MockPayment[] = []
export const refunds: MockRefund[] = [
  { id: 1, refundSn: 'RF202608181001', orderId: 6, orderSn: '20260818100660006', paymentSn: 'PAY202608181001', memberId: 1001, refundAmount: 599, reason: '商品有质量问题，申请退货退款', refundType: 2, returnCompany: '顺丰速运', returnSn: 'SF1389123456111', status: 2, auditBy: 'admin', auditTime: fmtDateTime(daysAgo(11)), applyTime: fmtDateTime(daysAgo(11)), refundTime: fmtDateTime(daysAgo(9)), createTime: fmtDateTime(daysAgo(11)) }
]

// ---------- 优惠券 ----------

export const couponTemplates = [
  { id: 1, name: '新人专享 5 元无门槛券', type: 0, amount: 5, threshold: 0, totalCount: 10000, receivedCount: 3215, perLimit: 1, useStartTime: fmtDateTime(daysAgo(10)), useEndTime: fmtDateTime(daysAgo(-20)), status: 1, createTime: fmtDateTime(daysAgo(10)) },
  { id: 2, name: '满 100 减 10 元', type: 1, amount: 10, threshold: 100, totalCount: 5000, receivedCount: 1832, perLimit: 2, useStartTime: fmtDateTime(daysAgo(8)), useEndTime: fmtDateTime(daysAgo(-15)), status: 1, createTime: fmtDateTime(daysAgo(8)) },
  { id: 3, name: '满 300 减 30 元', type: 1, amount: 30, threshold: 300, totalCount: 3000, receivedCount: 976, perLimit: 2, useStartTime: fmtDateTime(daysAgo(6)), useEndTime: fmtDateTime(daysAgo(-10)), status: 1, createTime: fmtDateTime(daysAgo(6)) },
  { id: 4, name: '满 500 减 50 元', type: 1, amount: 50, threshold: 500, totalCount: 2000, receivedCount: 432, perLimit: 1, useStartTime: fmtDateTime(daysAgo(4)), useEndTime: fmtDateTime(daysAgo(-30)), status: 1, createTime: fmtDateTime(daysAgo(4)) },
  { id: 5, name: '大促满 1000 减 120 元', type: 1, amount: 120, threshold: 1000, totalCount: 1000, receivedCount: 1000, perLimit: 1, useStartTime: fmtDateTime(daysAgo(20)), useEndTime: fmtDateTime(daysAgo(5)), status: 0, createTime: fmtDateTime(daysAgo(20)) }
]

export interface MockMyCoupon {
  id: number
  couponId: number
  name: string
  type: number
  amount: number
  threshold: number
  status: number
  receiveTime: string
  useTime?: string
  expireTime?: string
  orderSn?: string
}

export const myCoupons: MockMyCoupon[] = [
  { id: 1, couponId: 2, name: '满 100 减 10 元', type: 1, amount: 10, threshold: 100, status: 0, receiveTime: fmtDateTime(daysAgo(5)), expireTime: fmtDateTime(daysAgo(-15)) },
  { id: 2, couponId: 3, name: '满 300 减 30 元', type: 1, amount: 30, threshold: 300, status: 0, receiveTime: fmtDateTime(daysAgo(4)), expireTime: fmtDateTime(daysAgo(-10)) },
  { id: 3, couponId: 4, name: '满 500 减 50 元', type: 1, amount: 50, threshold: 500, status: 2, receiveTime: fmtDateTime(daysAgo(18)), useTime: fmtDateTime(daysAgo(15)), expireTime: fmtDateTime(daysAgo(-30)), orderSn: '20260825100440004' },
  { id: 4, couponId: 1, name: '新人专享 5 元无门槛券', type: 0, amount: 5, threshold: 0, status: 3, receiveTime: fmtDateTime(daysAgo(40)), expireTime: fmtDateTime(daysAgo(10)) }
]

// ---------- 秒杀 ----------

export interface MockSeckillSession {
  id: number
  name: string
  startTime: string
  endTime: string
  status: number
}

export interface MockSeckillProduct {
  id: number
  sessionId: number
  spuId: number
  skuId: number
  spuName: string
  skuCode: string
  spec: string
  pic: string
  price: number
  seckillPrice: number
  seckillStock: number
  limitPerUser: number
  skuStock: number
  remainStock: number
  status: number
}

// 场次时间相对当前时间动态生成：场次1 已结束、场次2 进行中、场次3 即将开始
export const seckillSessions: MockSeckillSession[] = [
  { id: 1, name: '08-29 10:00 数码专场', startTime: fmtDateTime(daysAgo(3)), endTime: fmtDateTime(new Date(daysAgo(3).getTime() + 2 * 3600_000)), status: 1 },
  { id: 2, name: '今日 14:00 秒杀专场', startTime: fmtDateTime(new Date(Date.now() - 2 * 3600_000)), endTime: fmtDateTime(new Date(Date.now() + 2 * 3600_000)), status: 1 },
  { id: 3, name: '明日 10:00 家电专场', startTime: fmtDateTime(new Date(Date.now() + 16 * 3600_000)), endTime: fmtDateTime(new Date(Date.now() + 20 * 3600_000)), status: 1 }
]

export const seckillProducts: MockSeckillProduct[] = [
  { id: 1, sessionId: 1, spuId: 1, skuId: 101, spuName: 'Apple iPhone 15 Pro', skuCode: 'SKU20260827101', spec: '256GB 原色钛金属', pic: '/mock-imgs/p1.svg', price: 7999, seckillPrice: 6999, seckillStock: 50, limitPerUser: 1, skuStock: 320, remainStock: 0, status: 1 },
  { id: 2, sessionId: 1, spuId: 6, skuId: 109, spuName: '索尼 WH-1000XM5 降噪耳机', skuCode: 'SKU20260827601', spec: '黑色', pic: '/mock-imgs/p6.svg', price: 2499, seckillPrice: 1899, seckillStock: 100, limitPerUser: 1, skuStock: 380, remainStock: 12, status: 1 },
  { id: 3, sessionId: 2, spuId: 3, skuId: 105, spuName: '小米 14', skuCode: 'SKU20260827301', spec: '12GB+256GB 黑色', pic: '/mock-imgs/p3.svg', price: 3999, seckillPrice: 3499, seckillStock: 80, limitPerUser: 1, skuStock: 500, remainStock: 46, status: 1 },
  { id: 4, sessionId: 2, spuId: 7, skuId: 110, spuName: '漫步者 NeoBuds Pro 2', skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, seckillPrice: 299, seckillStock: 200, limitPerUser: 2, skuStock: 1000, remainStock: 128, status: 1 },
  { id: 5, sessionId: 2, spuId: 10, skuId: 113, spuName: '戴尔 UltraSharp U2723QE 显示器', skuCode: 'SKU20260827101', spec: '27 英寸 4K', pic: '/mock-imgs/p10.svg', price: 3299, seckillPrice: 2799, seckillStock: 30, limitPerUser: 1, skuStock: 160, remainStock: 19, status: 1 },
  { id: 6, sessionId: 3, spuId: 13, skuId: 116, spuName: '海信 65E7N 65 英寸电视', skuCode: 'SKU20260827131', spec: '65 英寸 Mini LED', pic: '/mock-imgs/p13.svg', price: 3999, seckillPrice: 3299, seckillStock: 20, limitPerUser: 1, skuStock: 90, remainStock: 20, status: 1 },
  { id: 7, sessionId: 3, spuId: 14, skuId: 117, spuName: '海尔 501L 十字对开门冰箱', skuCode: 'SKU20260827141', spec: '501L 星蕴灰', pic: '/mock-imgs/p14.svg', price: 3499, seckillPrice: 2999, seckillStock: 15, limitPerUser: 1, skuStock: 70, remainStock: 15, status: 1 }
]

/** 场次 phase：disabled / upcoming / ongoing / finished（与后端一致） */
export function sessionPhase(s: MockSeckillSession): string {
  if (s.status !== 1) return 'disabled'
  const now = Date.now()
  const start = new Date(s.startTime.replace(' ', 'T')).getTime()
  const end = new Date(s.endTime.replace(' ', 'T')).getTime()
  if (now < start) return 'upcoming'
  if (now > end) return 'finished'
  return 'ongoing'
}

// ---------- 会员 / 地址 / 收藏 / 足迹 / 签到 ----------

export const profile = {
  id: 1001,
  username: 'demo',
  nickname: '演示用户',
  avatar: '',
  phone: '13800001234',
  email: 'demo@mall-practice.dev',
  gender: 1,
  birthday: '1995-06-18',
  level: 3,
  points: 1280,
  createTime: fmtDateTime(daysAgo(365))
}

export const pointLogs = [
  { id: 1, changeType: 1, changePoint: 20, pointAfter: 1280, orderSn: '20260825100440004', createTime: fmtDateTime(daysAgo(2)) },
  { id: 2, changeType: 4, changePoint: -50, pointAfter: 1260, orderSn: '20260825100440004', createTime: fmtDateTime(daysAgo(15)) },
  { id: 3, changeType: 1, changePoint: 10, pointAfter: 1310, orderSn: '', createTime: fmtDateTime(daysAgo(18)) },
  { id: 4, changeType: 2, changePoint: 5, pointAfter: 1300, orderSn: '', createTime: fmtDateTime(daysAgo(20)) }
]

export const addresses = [
  { id: 1, receiverName: '张伟', receiverPhone: '13800001234', province: '广东省', city: '深圳市', district: '南山区', detailAddress: '科技园南路 1 号 3 栋 501 室', defaultFlag: 1 },
  { id: 2, receiverName: '张伟', receiverPhone: '13800001234', province: '广东省', city: '广州市', district: '天河区', detailAddress: '体育西路 100 号 2 栋 1203 室', defaultFlag: 0 },
  { id: 3, receiverName: '李娜', receiverPhone: '13900005678', province: '北京市', city: '北京市', district: '海淀区', detailAddress: '中关村大街 27 号', defaultFlag: 0 }
]

export const favorites = [
  { favoriteId: 1, spuId: 2, createTime: fmtDateTime(daysAgo(6)) },
  { favoriteId: 2, spuId: 6, createTime: fmtDateTime(daysAgo(4)) },
  { favoriteId: 3, spuId: 13, createTime: fmtDateTime(daysAgo(2)) }
]

export const browseHistory = [
  { spuId: 3, viewTime: Date.now() - 3600_000, },
  { spuId: 7, viewTime: Date.now() - 7200_000 },
  { spuId: 1, viewTime: Date.now() - 86400_000 }
]

export const checkinState = {
  date: fmtDate(new Date()),
  signedToday: false,
  monthDays: 8,
  streakDays: 5
}

// ---------- 评论 ----------

export const myComments = [
  { id: 1, orderItemId: 5, spuId: 10, spuName: '戴尔 UltraSharp U2723QE 显示器', skuSpec: '27 英寸 4K', pic: '/mock-imgs/p10.svg', rating: 5, content: '色彩准确，4K 办公体验很好，USB-C 反向充电很实用。', pics: '', reply: '感谢您的认可，祝您使用愉快！', status: 1, createTime: fmtDateTime(daysAgo(14)) },
  { id: 2, orderItemId: 6, spuId: 12, spuName: '小米机械键盘 87 键', skuSpec: '87 键 黑灰', pic: '/mock-imgs/p12.svg', rating: 4, content: '手感不错，Gasket 结构声音好听，就是键帽容易打油。', pics: '', reply: '', status: 1, createTime: fmtDateTime(daysAgo(14)) }
]

// ---------- 点赞状态（内存 Set） ----------

export const likedSpuIds = new Set<number>([6, 13])
export const likeCountMap = new Map<number, number>([
  [1, 128], [2, 96], [3, 342], [4, 88], [5, 76], [6, 523], [7, 618], [8, 45], [9, 67], [10, 213], [11, 892], [12, 456], [13, 156], [14, 98], [15, 234]
])

// ---------- 通用工具 ----------

export function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v))
}

export function paginate<T>(list: T[], page: number, size: number) {
  const p = Math.max(1, page || 1)
  const s = Math.max(1, size || 10)
  return { records: list.slice((p - 1) * s, p * s), total: list.length }
}
