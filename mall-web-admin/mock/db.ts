// ---------- Mock 演示数据（内存态；刷新页面后重置） ----------
// 菜单/权限与 sql/mall.sql admin_menu 种子对齐（path 调整为前端路由实际路径）；
// 商品/订单/秒杀等与 mall-web-portal mock 数据同源，保证演示一致性。

/** 时间工具 */
function pad(n: number) {
  return n < 10 ? '0' + n : String(n)
}
export function fmtDateTime(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
export function daysAgo(n: number): Date {
  return new Date(Date.now() - n * 86400_000)
}
export function hoursAgo(h: number): Date {
  return new Date(Date.now() - h * 3600_000)
}

// ---------- 菜单树（sql/mall.sql admin_menu 真实结构；path 对齐前端路由） ----------

export interface MockMenu {
  id: number
  parentId: number
  name: string
  type: number
  path: string
  perms: string | null
  icon: string | null
  sort: number
  status: number
  children: MockMenu[]
}

export const menuTree: MockMenu[] = [
  {
    id: 1, parentId: 0, name: '系统管理', type: 1, path: '', perms: null, icon: 'setting', sort: 1, status: 1,
    children: [
      { id: 2, parentId: 1, name: '用户管理', type: 2, path: '/user', perms: 'system:user:list', icon: 'user', sort: 1, status: 1, children: [] },
      { id: 3, parentId: 1, name: '角色管理', type: 2, path: '/role', perms: 'system:role:list', icon: 'role', sort: 2, status: 1, children: [] },
      { id: 4, parentId: 1, name: '菜单管理', type: 2, path: '/menu', perms: 'system:menu:list', icon: 'menu', sort: 3, status: 1, children: [] }
    ]
  },
  {
    id: 20, parentId: 0, name: '商品管理', type: 1, path: '', perms: null, icon: 'product', sort: 2, status: 1,
    children: [
      { id: 21, parentId: 20, name: '分类管理', type: 2, path: '/category', perms: 'product:category:list', icon: '', sort: 1, status: 1, children: [] },
      { id: 22, parentId: 20, name: '品牌管理', type: 2, path: '/brand', perms: 'product:brand:list', icon: '', sort: 2, status: 1, children: [] },
      { id: 23, parentId: 20, name: '商品管理', type: 2, path: '/product', perms: 'product:spu:list', icon: '', sort: 3, status: 1, children: [] },
      { id: 24, parentId: 20, name: '供应商管理', type: 2, path: '/supplier', perms: 'product:supplier:list', icon: '', sort: 4, status: 1, children: [] },
      { id: 25, parentId: 20, name: '采购管理', type: 2, path: '/purchase', perms: 'product:purchase:list', icon: '', sort: 5, status: 1, children: [] },
      { id: 26, parentId: 20, name: '库存管理', type: 2, path: '/stock', perms: 'product:stock:list', icon: '', sort: 6, status: 1, children: [] },
      { id: 51, parentId: 20, name: '评价管理', type: 2, path: '/comment', perms: 'product:comment:list', icon: '', sort: 7, status: 1, children: [] }
    ]
  },
  {
    id: 52, parentId: 0, name: '营销管理', type: 1, path: '', perms: null, icon: 'coupon', sort: 3, status: 1,
    children: [
      { id: 53, parentId: 52, name: '优惠券模板', type: 2, path: '/coupon-template', perms: 'coupon:template:list', icon: '', sort: 1, status: 1, children: [] }
    ]
  },
  {
    id: 58, parentId: 0, name: '交易管理', type: 1, path: '', perms: null, icon: 'trade', sort: 4, status: 1,
    children: [
      { id: 59, parentId: 58, name: '订单管理', type: 2, path: '/order', perms: 'order:list', icon: '', sort: 1, status: 1, children: [] },
      { id: 61, parentId: 58, name: '退款管理', type: 2, path: '/refund', perms: 'refund:list', icon: '', sort: 2, status: 1, children: [] }
    ]
  },
  {
    id: 64, parentId: 0, name: '数据看板', type: 2, path: '/dashboard', perms: 'dashboard:view', icon: 'dataAnalysis', sort: 5, status: 1, children: []
  },
  {
    id: 65, parentId: 0, name: '秒杀管理', type: 1, path: '', perms: null, icon: 'Timer', sort: 6, status: 1,
    children: [
      { id: 66, parentId: 65, name: '秒杀场次', type: 2, path: '/seckill/session', perms: 'seckill:session:list', icon: '', sort: 1, status: 1, children: [] },
      { id: 71, parentId: 65, name: '秒杀商品', type: 2, path: '/seckill/product', perms: 'seckill:product:list', icon: '', sort: 2, status: 1, children: [] }
    ]
  },
  {
    id: 72, parentId: 0, name: 'AI 助手', type: 2, path: '/ai', perms: 'ai:chat', icon: 'chat', sort: 7, status: 1,
    children: []
  }
]

// ---------- 用户 / 角色 ----------

export const users = [
  { id: 1, username: 'admin', nickname: '超级管理员', phone: '13800000001', email: 'admin@mall-practice.dev', status: 1, lastLoginTime: fmtDateTime(hoursAgo(2)), createTime: fmtDateTime(daysAgo(400)) },
  { id: 2, username: 'operator', nickname: '运营专员', phone: '13800000002', email: 'operator@mall-practice.dev', status: 1, lastLoginTime: fmtDateTime(hoursAgo(5)), createTime: fmtDateTime(daysAgo(300)) },
  { id: 3, username: 'cs01', nickname: '客服小美', phone: '13800000003', email: 'cs01@mall-practice.dev', status: 1, lastLoginTime: fmtDateTime(daysAgo(1)), createTime: fmtDateTime(daysAgo(260)) },
  { id: 4, username: 'warehouse', nickname: '仓管小李', phone: '13800000004', email: 'warehouse@mall-practice.dev', status: 1, lastLoginTime: fmtDateTime(daysAgo(1)), createTime: fmtDateTime(daysAgo(200)) },
  { id: 5, username: 'finance', nickname: '财务王姐', phone: '13800000005', email: 'finance@mall-practice.dev', status: 0, lastLoginTime: fmtDateTime(daysAgo(30)), createTime: fmtDateTime(daysAgo(180)) }
]

export const roles = [
  { id: 1, name: '超级管理员', code: 'SUPER_ADMIN', description: '拥有全部菜单与按钮权限', status: 1, createTime: fmtDateTime(daysAgo(400)) },
  { id: 2, name: '运营', code: 'OPERATOR', description: '商品、营销、秒杀运营配置', status: 1, createTime: fmtDateTime(daysAgo(300)) },
  { id: 3, name: '客服', code: 'CUSTOMER_SERVICE', description: '订单、退款、评价处理', status: 1, createTime: fmtDateTime(daysAgo(260)) },
  { id: 4, name: '仓库管理员', code: 'WAREHOUSE', description: '供应商、采购、库存管理', status: 1, createTime: fmtDateTime(daysAgo(200)) }
]

export const roleMenus: Record<number, number[]> = {
  1: [1, 2, 3, 4, 20, 21, 22, 23, 24, 25, 26, 51, 52, 53, 58, 59, 61, 64, 65, 66, 71, 72],
  2: [20, 21, 22, 23, 24, 51, 52, 53, 64, 65, 66, 71],
  3: [58, 59, 61, 51, 64],
  4: [20, 24, 25, 26]
}

export const userRoles: Record<number, number[]> = {
  1: [1],
  2: [2],
  3: [3],
  4: [4],
  5: [3]
}

// ---------- 分类 / 品牌 / 商品 ----------

export const categories = [
  { id: 1, parentId: 0, name: '手机数码', level: 1, icon: '', sort: 1, status: 1, children: [
    { id: 11, parentId: 1, name: '手机', level: 2, icon: '', sort: 1, status: 1, children: [] },
    { id: 12, parentId: 1, name: '平板电脑', level: 2, icon: '', sort: 2, status: 1, children: [] },
    { id: 13, parentId: 1, name: '耳机音箱', level: 2, icon: '', sort: 3, status: 1, children: [] }
  ]},
  { id: 2, parentId: 0, name: '电脑办公', level: 1, icon: '', sort: 2, status: 1, children: [
    { id: 21, parentId: 2, name: '笔记本电脑', level: 2, icon: '', sort: 1, status: 1, children: [] },
    { id: 22, parentId: 2, name: '显示器', level: 2, icon: '', sort: 2, status: 1, children: [] },
    { id: 23, parentId: 2, name: '键鼠外设', level: 2, icon: '', sort: 3, status: 1, children: [] }
  ]},
  { id: 3, parentId: 0, name: '家用电器', level: 1, icon: '', sort: 3, status: 1, children: [
    { id: 31, parentId: 3, name: '电视', level: 2, icon: '', sort: 1, status: 1, children: [] },
    { id: 32, parentId: 3, name: '冰箱', level: 2, icon: '', sort: 2, status: 1, children: [] },
    { id: 33, parentId: 3, name: '厨房电器', level: 2, icon: '', sort: 3, status: 1, children: [] }
  ]}
]

export const brands = [
  { id: 1, name: 'Apple', logo: '', description: '全球领先的消费电子品牌', sort: 1, status: 1, createTime: fmtDateTime(daysAgo(400)) },
  { id: 2, name: '华为', logo: '', description: 'ICT 与智能终端领先品牌', sort: 2, status: 1, createTime: fmtDateTime(daysAgo(400)) },
  { id: 3, name: '小米', logo: '', description: '智能硬件与电子产品品牌', sort: 3, status: 1, createTime: fmtDateTime(daysAgo(380)) },
  { id: 4, name: '索尼', logo: '', description: '影音数码知名品牌', sort: 4, status: 1, createTime: fmtDateTime(daysAgo(380)) },
  { id: 5, name: '戴尔', logo: '', description: '电脑显示器专业品牌', sort: 5, status: 1, createTime: fmtDateTime(daysAgo(360)) },
  { id: 6, name: '漫步者', logo: '', description: '音频设备国货品牌', sort: 6, status: 1, createTime: fmtDateTime(daysAgo(360)) },
  { id: 7, name: '海信', logo: '', description: '家电与显示技术品牌', sort: 7, status: 1, createTime: fmtDateTime(daysAgo(340)) },
  { id: 8, name: '海尔', logo: '', description: '白色家电头部品牌', sort: 8, status: 1, createTime: fmtDateTime(daysAgo(340)) },
  { id: 9, name: '美的', logo: '', description: '家用电器综合品牌', sort: 9, status: 1, createTime: fmtDateTime(daysAgo(320)) }
]

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
  { id: 11, spuCode: 'SPU202608270011', categoryId: 23, brandId: 3, name: '小米无线鼠标', subtitle: '静音按键 · 三模连接 · 人体工学设计', mainPic: '/mock-imgs/p11.svg', pics: '/mock-imgs/p11.svg,/mock-imgs/p12.svg,/mock-imgs/p13.svg', unit: '个', detail: '静音微动按键，支持蓝牙/2.4G/有线三模连接，人体工学造型，2400DPI。', status: 1, sort: 11, sales: 15678, createTime: fmtDateTime(daysAgo(60)) },
  { id: 12, spuCode: 'SPU202608270012', categoryId: 23, brandId: 3, name: '小米机械键盘 87 键', subtitle: 'Gasket 结构 · 全键热插拔 · 三模连接', mainPic: '/mock-imgs/p12.svg', pics: '/mock-imgs/p12.svg,/mock-imgs/p13.svg,/mock-imgs/p14.svg', unit: '个', detail: 'Gasket 结构手感软弹，全键热插拔，支持有线/蓝牙/2.4G 三模，RGB 背光。', status: 1, sort: 12, sales: 4321, createTime: fmtDateTime(daysAgo(55)) },
  { id: 13, spuCode: 'SPU202608270013', categoryId: 31, brandId: 7, name: '海信 65E7N 65 英寸电视', subtitle: 'Mini LED · 144Hz 高刷 · ULED 画质引擎', mainPic: '/mock-imgs/p13.svg', pics: '/mock-imgs/p13.svg,/mock-imgs/p14.svg,/mock-imgs/p15.svg', unit: '台', detail: '65 英寸 Mini LED 背光，144Hz 原生高刷，ULED 画质引擎，4GB+64GB 大存储。', status: 1, sort: 13, sales: 876, createTime: fmtDateTime(daysAgo(50)) },
  { id: 14, spuCode: 'SPU202608270014', categoryId: 32, brandId: 8, name: '海尔 501L 十字对开门冰箱', subtitle: '全空间保鲜 · 一级能效 · 阻氧干湿分储', mainPic: '/mock-imgs/p14.svg', pics: '/mock-imgs/p14.svg,/mock-imgs/p15.svg,/mock-imgs/p1.svg', unit: '台', detail: '501L 大容量十字对开门，全空间保鲜科技，一级能效，阻氧干湿分储抽屉。', status: 1, sort: 14, sales: 654, createTime: fmtDateTime(daysAgo(45)) },
  { id: 15, spuCode: 'SPU202608270015', categoryId: 33, brandId: 9, name: '美的微波炉 M1-L213B', subtitle: '21L 家用 · 智能解冻 · 平板加热', mainPic: '/mock-imgs/p15.svg', pics: '/mock-imgs/p15.svg,/mock-imgs/p1.svg,/mock-imgs/p2.svg', unit: '台', detail: '21L 家用平板微波炉，智能解冻，五档火力，易清洁内胆，一级能效。', status: 1, sort: 15, sales: 3209, createTime: fmtDateTime(daysAgo(40)) },
  { id: 16, spuCode: 'SPU202609010001', categoryId: 33, brandId: 9, name: '美的电饭煲 4L', subtitle: 'IH 电磁加热 · 24 小时预约 · 智能菜单', mainPic: '/mock-imgs/p1.svg', pics: '/mock-imgs/p1.svg,/mock-imgs/p2.svg', unit: '台', detail: '4L IH 电磁加热电饭煲，24 小时智能预约，十多种烹饪菜单。', status: 0, sort: 16, sales: 0, createTime: fmtDateTime(daysAgo(3)) }
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
  { id: 118, spuId: 15, skuCode: 'SKU20260827151', spec: '21L 白色', price: 599, stock: 450, lowStock: 25, status: 1, version: 1 },
  { id: 119, spuId: 16, skuCode: 'SKU20260901101', spec: '4L 香槟金', price: 499, stock: 0, lowStock: 10, status: 0, version: 1 }
]

export function spuById(id: number) {
  return spus.find((s) => s.id === id)
}
export function skuById(id: number) {
  return skus.find((s) => s.id === id)
}

// ---------- 供应商 / 采购单 ----------

export const suppliers = [
  { id: 1, name: '深圳市华强数码科技有限公司', contact: '陈经理', phone: '0755-88880001', address: '深圳市福田区华强北街道 100 号', remark: '手机/平板类目核心供应商', status: 1, createTime: fmtDateTime(daysAgo(300)) },
  { id: 2, name: '东莞金立电子有限公司', contact: '刘主管', phone: '0769-22220002', address: '东莞市长安镇工业大道 8 号', remark: '耳机音箱类目供应商', status: 1, createTime: fmtDateTime(daysAgo(280)) },
  { id: 3, name: '昆山联合电脑配件厂', contact: '周经理', phone: '0512-66660003', address: '昆山市开发区创业路 66 号', remark: '键鼠外设类目供应商', status: 1, createTime: fmtDateTime(daysAgo(250)) },
  { id: 4, name: '青岛海达家电贸易有限公司', contact: '孙总', phone: '0532-88880004', address: '青岛市崂山区海尔路 1 号', remark: '大家电类目供应商', status: 1, createTime: fmtDateTime(daysAgo(220)) },
  { id: 5, name: '佛山市顺德顺发电器厂', contact: '吴厂长', phone: '0757-28880005', address: '佛山市顺德区容桂街道', remark: '厨房电器类目供应商', status: 0, createTime: fmtDateTime(daysAgo(180)) }
]

export interface MockPurchase {
  id: number
  purchaseSn: string
  supplierId: number
  totalAmount: number
  status: number
  auditBy?: string
  auditTime?: string
  createTime: string
}

export interface MockPurchaseItem {
  id: number
  purchaseId: number
  skuId: number
  quantity: number
  receivedQuantity: number
  purchasePrice: number
}

export const purchases: MockPurchase[] = [
  { id: 1, purchaseSn: 'CG20260820001', supplierId: 1, totalAmount: 1599800, status: 2, auditBy: 'admin', auditTime: fmtDateTime(daysAgo(10)), createTime: fmtDateTime(daysAgo(12)) },
  { id: 2, purchaseSn: 'CG20260825002', supplierId: 2, totalAmount: 199500, status: 1, auditBy: 'admin', auditTime: fmtDateTime(daysAgo(6)), createTime: fmtDateTime(daysAgo(7)) },
  { id: 3, purchaseSn: 'CG20260828003', supplierId: 4, totalAmount: 349900, status: 0, createTime: fmtDateTime(daysAgo(2)) },
  { id: 4, purchaseSn: 'CG20260815004', supplierId: 3, totalAmount: 89700, status: 3, auditBy: 'admin', auditTime: fmtDateTime(daysAgo(20)), createTime: fmtDateTime(daysAgo(22)) }
]

export const purchaseItems: MockPurchaseItem[] = [
  { id: 1, purchaseId: 1, skuId: 101, quantity: 100, receivedQuantity: 100, purchasePrice: 7200 },
  { id: 2, purchaseId: 1, skuId: 103, quantity: 80, receivedQuantity: 80, purchasePrice: 6400 },
  { id: 3, purchaseId: 1, skuId: 105, quantity: 120, receivedQuantity: 120, purchasePrice: 3650 },
  { id: 4, purchaseId: 2, skuId: 109, quantity: 50, receivedQuantity: 0, purchasePrice: 2190 },
  { id: 5, purchaseId: 2, skuId: 110, quantity: 200, receivedQuantity: 0, purchasePrice: 330 },
  { id: 6, purchaseId: 3, skuId: 116, quantity: 60, receivedQuantity: 0, purchasePrice: 3499 },
  { id: 7, purchaseId: 3, skuId: 117, quantity: 40, receivedQuantity: 0, purchasePrice: 2999 },
  { id: 8, purchaseId: 4, skuId: 114, quantity: 100, receivedQuantity: 60, purchasePrice: 599 },
  { id: 9, purchaseId: 4, skuId: 115, quantity: 100, receivedQuantity: 40, purchasePrice: 260 }
]

export const stockLogs = [
  { id: 1, skuId: 101, bizSn: 'CG20260820001', changeType: 3, changeCount: 100, stockBefore: 220, stockAfter: 320, createTime: fmtDateTime(daysAgo(8)) },
  { id: 2, skuId: 105, bizSn: 'CG20260820001', changeType: 3, changeCount: 120, stockBefore: 380, stockAfter: 500, createTime: fmtDateTime(daysAgo(8)) },
  { id: 3, skuId: 101, bizSn: 'SO202608281002', changeType: 1, changeCount: -1, stockBefore: 321, stockAfter: 320, createTime: fmtDateTime(daysAgo(1)) },
  { id: 4, skuId: 114, bizSn: 'PD20260901001', changeType: 2, changeCount: -40, stockBefore: 840, stockAfter: 800, createTime: fmtDateTime(daysAgo(3)) }
]

// ---------- 订单 / 退款 ----------

export interface MockOrder {
  id: number
  orderSn: string
  requestId: string
  memberId: number
  memberName: string
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
}

export const orders: MockOrder[] = [
  { id: 1, orderSn: '20260827100120001', requestId: 'req-1001', memberId: 1001, memberName: 'demo', totalAmount: 4398, freightAmount: 0, couponAmount: 30, discountAmount: 30, payAmount: 4368, payType: 1, status: 2, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', remark: '工作日白天派送', payTime: fmtDateTime(daysAgo(3)), deliveryCompany: '顺丰速运', deliverySn: 'SF1389123456789', deliveryTime: fmtDateTime(daysAgo(2)), createTime: fmtDateTime(daysAgo(3)) },
  { id: 2, orderSn: '20260828100220002', requestId: 'req-1002', memberId: 1001, memberName: 'demo', totalAmount: 7999, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 7999, payType: 1, status: 1, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(1)), createTime: fmtDateTime(daysAgo(1)) },
  { id: 3, orderSn: '20260829100330003', requestId: 'req-1003', memberId: 1001, memberName: 'demo', totalAmount: 399, freightAmount: 10, couponAmount: 0, discountAmount: 0, payAmount: 409, payType: 1, status: 0, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', createTime: fmtDateTime(hoursAgo(5)) },
  { id: 4, orderSn: '20260825100440004', requestId: 'req-1004', memberId: 1001, memberName: 'demo', totalAmount: 4998, freightAmount: 0, couponAmount: 50, discountAmount: 50, payAmount: 4948, payType: 2, status: 3, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(20)), deliveryCompany: '京东物流', deliverySn: 'JD8890123456', deliveryTime: fmtDateTime(daysAgo(18)), receiveTime: fmtDateTime(daysAgo(15)), finishTime: fmtDateTime(daysAgo(15)), createTime: fmtDateTime(daysAgo(20)) },
  { id: 5, orderSn: '20260822100550005', requestId: 'req-1005', memberId: 1001, memberName: 'demo', totalAmount: 2499, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 2499, payType: 1, status: 4, receiverName: '张伟', receiverPhone: '138****1234', receiverAddress: '广东省深圳市南山区科技园南路 1 号', payTime: fmtDateTime(daysAgo(9)), createTime: fmtDateTime(daysAgo(9)) },
  { id: 6, orderSn: '20260818100660006', requestId: 'req-1006', memberId: 1002, memberName: 'test01', totalAmount: 599, freightAmount: 0, couponAmount: 0, discountAmount: 0, payAmount: 599, payType: 1, status: 5, receiverName: '李娜', receiverPhone: '139****5678', receiverAddress: '北京市海淀区中关村大街 27 号', payTime: fmtDateTime(daysAgo(12)), deliveryCompany: '顺丰速运', deliverySn: 'SF1389123456000', deliveryTime: fmtDateTime(daysAgo(11)), receiveTime: fmtDateTime(daysAgo(9)), createTime: fmtDateTime(daysAgo(12)) }
]

export const orderItems: MockOrderItem[] = [
  { id: 1, orderId: 1, orderSn: '20260827100120001', spuId: 3, spuName: '小米 14', skuId: 105, skuCode: 'SKU20260827301', spec: '12GB+256GB 黑色', pic: '/mock-imgs/p3.svg', price: 3999, quantity: 1, subtotal: 3999 },
  { id: 2, orderId: 1, orderSn: '20260827100120001', spuId: 7, spuName: '漫步者 NeoBuds Pro 2', skuId: 110, skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, quantity: 1, subtotal: 399 },
  { id: 3, orderId: 2, orderSn: '20260828100220002', spuId: 1, spuName: 'Apple iPhone 15 Pro', skuId: 101, skuCode: 'SKU20260827101', spec: '256GB 原色钛金属', pic: '/mock-imgs/p1.svg', price: 7999, quantity: 1, subtotal: 7999 },
  { id: 4, orderId: 3, orderSn: '20260829100330003', spuId: 7, spuName: '漫步者 NeoBuds Pro 2', skuId: 110, skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, quantity: 1, subtotal: 399 },
  { id: 5, orderId: 4, orderSn: '20260825100440004', spuId: 10, spuName: '戴尔 UltraSharp U2723QE 显示器', skuId: 113, skuCode: 'SKU20260827101', spec: '27 英寸 4K', pic: '/mock-imgs/p10.svg', price: 3299, quantity: 1, subtotal: 3299 },
  { id: 6, orderId: 4, orderSn: '20260825100440004', spuId: 12, spuName: '小米机械键盘 87 键', skuId: 115, skuCode: 'SKU20260827121', spec: '87 键 黑灰', pic: '/mock-imgs/p12.svg', price: 299, quantity: 1, subtotal: 299 },
  { id: 7, orderId: 4, orderSn: '20260825100440004', spuId: 11, spuName: '小米无线鼠标', skuId: 114, skuCode: 'SKU20260827112', spec: '深灰色', pic: '/mock-imgs/p11.svg', price: 699, quantity: 2, subtotal: 1398 },
  { id: 8, orderId: 5, orderSn: '20260822100550005', spuId: 6, spuName: '索尼 WH-1000XM5 降噪耳机', skuId: 109, skuCode: 'SKU20260827601', spec: '黑色', pic: '/mock-imgs/p6.svg', price: 2499, quantity: 1, subtotal: 2499 },
  { id: 9, orderId: 6, orderSn: '20260818100660006', spuId: 15, spuName: '美的微波炉 M1-L213B', skuId: 118, skuCode: 'SKU20260827151', spec: '21L 白色', pic: '/mock-imgs/p15.svg', price: 599, quantity: 1, subtotal: 599 }
]

export const refunds = [
  { id: 1, refundSn: 'RF202608181001', orderId: 6, orderSn: '20260818100660006', paymentSn: 'PAY202608181001', memberId: 1002, refundAmount: 599, reason: '商品有质量问题，申请退货退款', refundType: 2, returnCompany: '顺丰速运', returnSn: 'SF1389123456111', status: 2, auditBy: 'admin', auditTime: fmtDateTime(daysAgo(11)), applyTime: fmtDateTime(daysAgo(11)), refundTime: fmtDateTime(daysAgo(9)), createTime: fmtDateTime(daysAgo(11)) },
  { id: 2, refundSn: 'RF202608301002', orderId: 3, orderSn: '20260829100330003', paymentSn: 'PAY202608301002', memberId: 1001, refundAmount: 409, reason: '不想要了', refundType: 1, status: 0, applyTime: fmtDateTime(hoursAgo(20)), createTime: fmtDateTime(hoursAgo(20)) },
  { id: 3, refundSn: 'RF202609011003', orderId: 2, orderSn: '20260828100220002', paymentSn: 'PAY202609011003', memberId: 1001, refundAmount: 7999, reason: '七天无理由退货', refundType: 2, status: 1, auditBy: 'admin', auditTime: fmtDateTime(hoursAgo(3)), applyTime: fmtDateTime(hoursAgo(6)), createTime: fmtDateTime(hoursAgo(6)) }
]

// ---------- 优惠券模板 ----------

export const couponTemplates = [
  { id: 1, name: '新人专享 5 元无门槛券', type: 0, amount: 5, threshold: 0, totalCount: 10000, receivedCount: 3215, perLimit: 1, useStartTime: fmtDateTime(daysAgo(10)), useEndTime: fmtDateTime(daysAgo(-20)), status: 1, createTime: fmtDateTime(daysAgo(10)) },
  { id: 2, name: '满 100 减 10 元', type: 1, amount: 10, threshold: 100, totalCount: 5000, receivedCount: 1832, perLimit: 2, useStartTime: fmtDateTime(daysAgo(8)), useEndTime: fmtDateTime(daysAgo(-15)), status: 1, createTime: fmtDateTime(daysAgo(8)) },
  { id: 3, name: '满 300 减 30 元', type: 1, amount: 30, threshold: 300, totalCount: 3000, receivedCount: 976, perLimit: 2, useStartTime: fmtDateTime(daysAgo(6)), useEndTime: fmtDateTime(daysAgo(-10)), status: 1, createTime: fmtDateTime(daysAgo(6)) },
  { id: 4, name: '满 500 减 50 元', type: 1, amount: 50, threshold: 500, totalCount: 2000, receivedCount: 432, perLimit: 1, useStartTime: fmtDateTime(daysAgo(4)), useEndTime: fmtDateTime(daysAgo(-30)), status: 1, createTime: fmtDateTime(daysAgo(4)) },
  { id: 5, name: '大促满 1000 减 120 元', type: 1, amount: 120, threshold: 1000, totalCount: 1000, receivedCount: 1000, perLimit: 1, useStartTime: fmtDateTime(daysAgo(20)), useEndTime: fmtDateTime(daysAgo(5)), status: 0, createTime: fmtDateTime(daysAgo(20)) }
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
  status: number
}

export const seckillSessions: MockSeckillSession[] = [
  { id: 1, name: '08-29 10:00 数码专场', startTime: fmtDateTime(daysAgo(3)), endTime: fmtDateTime(new Date(daysAgo(3).getTime() + 2 * 3600_000)), status: 1 },
  { id: 2, name: '今日 14:00 秒杀专场', startTime: fmtDateTime(new Date(Date.now() - 2 * 3600_000)), endTime: fmtDateTime(new Date(Date.now() + 2 * 3600_000)), status: 1 },
  { id: 3, name: '明日 10:00 家电专场', startTime: fmtDateTime(new Date(Date.now() + 16 * 3600_000)), endTime: fmtDateTime(new Date(Date.now() + 20 * 3600_000)), status: 1 },
  { id: 4, name: '09-05 大促预热场', startTime: fmtDateTime(new Date(Date.now() + 96 * 3600_000)), endTime: fmtDateTime(new Date(Date.now() + 100 * 3600_000)), status: 0 }
]

export const seckillProducts: MockSeckillProduct[] = [
  { id: 1, sessionId: 1, spuId: 1, skuId: 101, spuName: 'Apple iPhone 15 Pro', skuCode: 'SKU20260827101', spec: '256GB 原色钛金属', pic: '/mock-imgs/p1.svg', price: 7999, seckillPrice: 6999, seckillStock: 50, limitPerUser: 1, skuStock: 320, status: 1 },
  { id: 2, sessionId: 1, spuId: 6, skuId: 109, spuName: '索尼 WH-1000XM5 降噪耳机', skuCode: 'SKU20260827601', spec: '黑色', pic: '/mock-imgs/p6.svg', price: 2499, seckillPrice: 1899, seckillStock: 100, limitPerUser: 1, skuStock: 380, status: 1 },
  { id: 3, sessionId: 2, spuId: 3, skuId: 105, spuName: '小米 14', skuCode: 'SKU20260827301', spec: '12GB+256GB 黑色', pic: '/mock-imgs/p3.svg', price: 3999, seckillPrice: 3499, seckillStock: 80, limitPerUser: 1, skuStock: 500, status: 1 },
  { id: 4, sessionId: 2, spuId: 7, skuId: 110, spuName: '漫步者 NeoBuds Pro 2', skuCode: 'SKU20260827701', spec: '白色', pic: '/mock-imgs/p7.svg', price: 399, seckillPrice: 299, seckillStock: 200, limitPerUser: 2, skuStock: 1000, status: 1 },
  { id: 5, sessionId: 2, spuId: 10, skuId: 113, spuName: '戴尔 UltraSharp U2723QE 显示器', skuCode: 'SKU20260827101', spec: '27 英寸 4K', pic: '/mock-imgs/p10.svg', price: 3299, seckillPrice: 2799, seckillStock: 30, limitPerUser: 1, skuStock: 160, status: 1 },
  { id: 6, sessionId: 3, spuId: 13, skuId: 116, spuName: '海信 65E7N 65 英寸电视', skuCode: 'SKU20260827131', spec: '65 英寸 Mini LED', pic: '/mock-imgs/p13.svg', price: 3999, seckillPrice: 3299, seckillStock: 20, limitPerUser: 1, skuStock: 90, status: 1 },
  { id: 7, sessionId: 3, spuId: 14, skuId: 117, spuName: '海尔 501L 十字对开门冰箱', skuCode: 'SKU20260827141', spec: '501L 星蕴灰', pic: '/mock-imgs/p14.svg', price: 3499, seckillPrice: 2999, seckillStock: 15, limitPerUser: 1, skuStock: 70, status: 1 }
]

// ---------- 评价 ----------

export const comments = [
  { id: 1, orderItemId: 5, orderSn: '20260825100440004', memberId: 1001, spuId: 10, spuName: '戴尔 UltraSharp U2723QE 显示器', rating: 5, content: '色彩准确，4K 办公体验很好，USB-C 反向充电很实用。', pics: [], status: 1, reply: '感谢您的认可，祝您使用愉快！', replyTime: fmtDateTime(daysAgo(13)), createTime: fmtDateTime(daysAgo(14)) },
  { id: 2, orderItemId: 6, orderSn: '20260825100440004', memberId: 1001, spuId: 12, spuName: '小米机械键盘 87 键', rating: 4, content: '手感不错，Gasket 结构声音好听，就是键帽容易打油。', pics: [], status: 1, reply: '', replyTime: '', createTime: fmtDateTime(daysAgo(14)) },
  { id: 3, orderItemId: 1, orderSn: '20260827100120001', memberId: 1001, spuId: 3, spuName: '小米 14', rating: 5, content: '拍照很强，徕卡影调直出效果惊艳，小屏手感一流。', pics: [], status: 1, reply: '感谢支持！', replyTime: fmtDateTime(daysAgo(2)), createTime: fmtDateTime(daysAgo(3)) },
  { id: 4, orderItemId: 2, orderSn: '20260827100120001', memberId: 1001, spuId: 7, spuName: '漫步者 NeoBuds Pro 2', rating: 5, content: '降噪效果明显，通勤神器，性价比很高。', pics: [], status: 1, reply: '', replyTime: '', createTime: fmtDateTime(daysAgo(3)) },
  { id: 5, orderItemId: 9, orderSn: '20260818100660006', memberId: 1002, spuId: 15, spuName: '美的微波炉 M1-L213B', rating: 3, content: '加热速度一般，但胜在便宜够用。', pics: [], status: 0, reply: '', replyTime: '', createTime: fmtDateTime(daysAgo(11)) },
  { id: 6, orderItemId: 3, orderSn: '20260828100220002', memberId: 1001, spuId: 1, spuName: 'Apple iPhone 15 Pro', rating: 5, content: '钛金属质感一流，续航比上一代明显提升。', pics: [], status: 1, reply: '感谢选购，祝您使用愉快！', replyTime: fmtDateTime(daysAgo(0)), createTime: fmtDateTime(daysAgo(1)) }
]

// ---------- 看板 ----------

export const dashboard = {
  today: { orderCount: 128, salesAmount: 45680.5, seckillOrderCount: 23 },
  member: { online: 156, dau: 3203, checkinToday: 1876, newMembersToday: 45 },
  trend7d: (() => {
    const rows = []
    for (let i = 6; i >= 0; i--) {
      const d = daysAgo(i)
      rows.push({
        date: `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
        orderCount: 96 + ((i * 37) % 60),
        salesAmount: Math.round((31800 + ((i * 5230) % 18000)) * 10) / 10
      })
    }
    return rows
  })(),
  salesRank: [
    { skuId: 110, sales: 12780, spuId: 7, spuName: '漫步者 NeoBuds Pro 2', pic: '/mock-imgs/p7.svg', price: 399 },
    { skuId: 114, sales: 15678, spuId: 11, spuName: '小米无线鼠标', pic: '/mock-imgs/p11.svg', price: 699 },
    { skuId: 105, sales: 8932, spuId: 3, spuName: '小米 14', pic: '/mock-imgs/p3.svg', price: 3999 },
    { skuId: 109, sales: 6543, spuId: 6, spuName: '索尼 WH-1000XM5 降噪耳机', pic: '/mock-imgs/p6.svg', price: 2499 },
    { skuId: 115, sales: 4321, spuId: 12, spuName: '小米机械键盘 87 键', pic: '/mock-imgs/p12.svg', price: 299 }
  ],
  viewsRank: [
    { spuId: 3, pv: 18234, spuName: '小米 14', mainPic: '/mock-imgs/p3.svg' },
    { spuId: 7, pv: 15672, spuName: '漫步者 NeoBuds Pro 2', mainPic: '/mock-imgs/p7.svg' },
    { spuId: 1, pv: 12456, spuName: 'Apple iPhone 15 Pro', mainPic: '/mock-imgs/p1.svg' },
    { spuId: 2, pv: 11234, spuName: '华为 Mate 60 Pro', mainPic: '/mock-imgs/p2.svg' },
    { spuId: 10, pv: 9876, spuName: '戴尔 UltraSharp U2723QE 显示器', mainPic: '/mock-imgs/p10.svg' }
  ],
  warnings: [
    { id: 111, skuCode: 'SKU20260827801', spuId: 8, stock: 8, lowStock: 8 },
    { id: 116, skuCode: 'SKU20260827131', spuId: 13, stock: 6, lowStock: 8 },
    { id: 117, skuCode: 'SKU20260827141', spuId: 14, stock: 4, lowStock: 6 }
  ]
}

// ---------- AI 助手（演示会话；发送新消息后由 /ai/chat/stream mock 自动追加，刷新重置） ----------

export interface MockAiSession {
  sessionId: string
  preview: string
  total: number
  createTime: string
}

export interface MockAiMessage {
  role: 'user' | 'assistant'
  content: string
}

export const aiSessions: MockAiSession[] = [
  { sessionId: 'mock-ai-session-0001', preview: '今天订单量和销售额怎么样？', total: 4, createTime: fmtDateTime(hoursAgo(3)) },
  { sessionId: 'mock-ai-session-0002', preview: '有哪些商品库存不足需要补货？', total: 2, createTime: fmtDateTime(daysAgo(1)) }
]

export const aiMessagesBySession: Record<string, MockAiMessage[]> = {
  'mock-ai-session-0001': [
    { role: 'user', content: '今天订单量和销售额怎么样？' },
    { role: 'assistant', content: '截至当前，今日共产生订单 128 单，销售额 ¥45,680.50，其中秒杀订单 23 单。整体交易状态健康，未发现异常波动。' },
    { role: 'user', content: '和昨天比呢？' },
    { role: 'assistant', content: '近 7 天订单量整体呈上升趋势，昨日订单 118 单、销售额 ¥40,910.00；今日较昨日订单量提升约 8.5%，销售额提升约 11.7%。' }
  ],
  'mock-ai-session-0002': [
    { role: 'user', content: '有哪些商品库存不足需要补货？' },
    { role: 'assistant', content: '当前有 3 个 SKU 触发库存预警：\n1. SKU20260827801 库存 8 件（预警线 8）\n2. SKU20260827131 库存 6 件（预警线 8）\n3. SKU20260827141 库存 4 件（预警线 6）\n建议尽快发起采购补货。' }
  ]
}

// ---------- 通用工具 ----------

export function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v))
}

export function paginate<T>(list: T[], page: number, size: number) {
  const p = Math.max(1, page || 1)
  const s = Math.max(1, size || 10)
  return { records: list.slice((p - 1) * s, p * s), total: list.length }
}
