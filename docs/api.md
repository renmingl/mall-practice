# 接口文档（API 总览）

> 本文档为 mall-practice 项目 README 拆分出的专题说明，返回 [README](../README.md)。

> **定位**：本页按模块 + 功能点汇总全部 HTTP 接口，用于总览"项目实现了哪些接口"；**逐字段参数、响应示例以各服务运行期接口文档为准**——启动服务后访问 `http://localhost:{端口}/doc.html`（springdoc-openapi 自动生成，随代码实时更新）。
>
> **统一入口**：所有 `/api/**` 请求经网关 `http://localhost:8080` 转发；`/api/admin/**` 需后台登录（JWT + RBAC），`/api/**` 买家接口需登录（白名单除外，如验证码/注册/登录/搜索/商品浏览）。

## 1. mall-auth 认证中心

### 1.1 买家认证（/api/auth，网关白名单放行）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/register | 注册（图形验证码 + 注册即登录） |
| POST | /api/auth/login | 登录（图形验证码） |
| POST | /api/auth/refresh | 刷新令牌 |
| POST | /api/auth/logout | 退出登录（access token 进黑名单） |
| POST | /api/auth/forgot-password | 找回密码（短信验证码，模拟） |

### 1.2 图形 / 短信验证码（/api/auth/captcha）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/auth/captcha | 获取图形验证码（{uuid, imgBase64}，Redis 存码 5 分钟） |
| GET | /api/auth/captcha/sms | 模拟短信验证码发送（开发期直接返回验证码，真实短信网关未接入） |

### 1.3 后台认证（/api/auth/admin）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/admin/login | 后台登录（RBAC：登录后权限标识写入 JWT） |
| GET | /api/auth/admin/me | 当前登录管理员信息（含角色/权限，刷新页面恢复登录态用） |

### 1.4 后台权限管理 RBAC（/api/admin/*）

**菜单 /api/admin/menu**（权限树：目录 → 菜单 → 按钮）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/menu/tree | 权限树（前端动态路由/菜单渲染用） |
| GET | /api/admin/menu/list | 全部菜单（权限树组件回显，扁平） |
| POST | /api/admin/menu | 新增菜单 |
| PUT | /api/admin/menu | 修改菜单 |
| DELETE | /api/admin/menu/{id} | 删除菜单（有子节点或已分配角色时拒绝） |

**角色 /api/admin/role**（RBAC 五表）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/role/list | 全部角色（下拉选择用） |
| GET | /api/admin/role/page | 角色分页 |
| POST | /api/admin/role | 新增角色 |
| PUT | /api/admin/role | 修改角色 |
| DELETE | /api/admin/role/{id} | 删除角色（已分配用户不可删） |
| GET | /api/admin/role/{id}/menus | 角色已分配菜单（回显） |
| PUT | /api/admin/role/{id}/menus | 分配菜单权限 |

**用户 /api/admin/user**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/user/page | 用户分页列表 |
| POST | /api/admin/user | 新增用户 |
| DELETE | /api/admin/user/{id} | 删除用户（超级管理员不可删） |
| PUT | /api/admin/user/{id}/password | 重置密码 |
| GET | /api/admin/user/{id}/roles | 用户已分配角色（回显） |
| PUT | /api/admin/user/{id}/roles | 分配角色 |

## 2. mall-admin 管理后台聚合层（无表，Feign 聚合）

### 2.1 数据看板（/api/admin/dashboard）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/dashboard/summary | 看板总览（一次聚合返回全部指标：今日概览/会员运营/订单趋势/销量榜/浏览榜/库存预警） |

## 3. mall-portal 前台商城聚合层（无表，编排聚合）

### 3.1 结算预览（/api/checkout）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/checkout/preview | 结算预览（购物车勾选商品 + 金额 + 可用优惠券聚合；下单仍走 order 服务实时校验） |

## 4. mall-member 会员中心

### 4.1 个人中心（/api/member）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/member/profile | 个人资料查询 |
| PUT | /api/member/profile | 修改资料（昵称/头像/邮箱/性别/生日） |
| GET | /api/member/level-info | 会员等级权益说明 |
| GET | /api/member/points | 积分余额 + 等级权益 |
| GET | /api/member/point-logs | 积分流水分页 |

### 4.2 收货地址（/api/member/address）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/member/address/list | 地址列表 |
| POST | /api/member/address | 新增地址 |
| GET | /api/member/address/{id} | 地址详情 |
| PUT | /api/member/address/{id} | 修改地址 |
| DELETE | /api/member/address/{id} | 删除地址 |
| PUT | /api/member/address/{id}/default | 设为默认地址 |

### 4.3 会员运营（/api/member/stats）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/member/stats/checkin | 签到（当天重复签到幂等，返回当月/连续天数） |
| GET | /api/member/stats/checkin/status | 签到状态（当月天数 + 今天是否已签 + 连续天数） |

## 5. mall-product 商品中心

### 5.1 前台商品（/api/product，白名单可匿名浏览）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/product/categories | 前台分类树（仅启用） |
| GET | /api/product/brands | 启用品牌列表（筛选下拉） |
| GET | /api/product/list | 商品列表（仅上架；分类/品牌/关键词筛选） |
| GET | /api/product/detail/{spuId} | 商品详情（缓存三防：穿透/击穿/雪崩） |
| GET | /api/product/hot | 热销 Top N |

### 5.2 商品运营（/api/product/stats）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/product/stats/sales-rank | 商品销量排行榜 Top N（10.4，ZSET 倒序） |
| POST | /api/product/stats/view | 浏览埋点（PV + UV + 浏览排行；登录用户记录足迹） |
| POST | /api/product/stats/like/{spuId} | 点赞（重复点赞幂等） |
| DELETE | /api/product/stats/like/{spuId} | 取消点赞 |
| GET | /api/product/stats/like/count/{spuId} | 点赞数 |
| GET | /api/product/stats/like/status/{spuId} | 是否已点赞 |
| GET | /api/product/stats/history | 最近浏览足迹（倒序，最多 50 条） |

### 5.3 商品评价（/api/comment）与收藏（/api/favorite）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/comment | 发表评价（收货后，order 校验订单项） |
| GET | /api/comment/spu/{spuId} | 商品评价列表（仅正常状态） |
| GET | /api/comment/mine | 我的评价 |
| POST | /api/favorite/{spuId} | 收藏商品 |
| DELETE | /api/favorite/{spuId} | 取消收藏 |
| GET | /api/favorite/list | 我的收藏列表 |
| GET | /api/favorite/status/{spuId} | 收藏状态 |

### 5.4 后台商品管理（/api/admin/product、brand、category、sku、stock、supplier、purchase、comment、upload）

**商品 /api/admin/product**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/product/list | 商品分页列表 |
| GET | /api/admin/product/{id} | 商品详情 |
| POST | /api/admin/product | 保存商品（新增/修改） |
| PUT | /api/admin/product/{id}/status | 上架/下架 |
| DELETE | /api/admin/product/{id} | 删除商品 |
| POST | /api/admin/product/preload | 缓存预热手动触发（场景 2.5） |

**品牌 /api/admin/brand**、**分类 /api/admin/category**、**供应商 /api/admin/supplier**（三者同为"分页 CRUD + 启停"结构）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/brand/list · /category/tree · /supplier/list | 分页/树查询 |
| POST | /api/admin/brand · /category · /supplier | 新增 |
| PUT | /api/admin/brand · /category · /supplier | 修改 |
| DELETE | /api/admin/{brand\|category\|supplier}/{id} | 删除 |
| PUT | /api/admin/{brand\|category\|supplier}/{id}/status | 启用/停用 |

**SKU /api/admin/sku**：GET /api/admin/sku/search —— SKU 远程搜索（keyword 非空，最多 20 条；秒杀配置选品用）。

**库存 /api/admin/stock**（进销存）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/stock/list | 实时库存分页（附预警标记） |
| GET | /api/admin/stock/logs | 库存流水分页（可按 SKU 过滤） |
| GET | /api/admin/stock/warning | 库存预警列表（stock < low_stock） |
| PUT | /api/admin/stock/check | 盘点调整（change_type=7，报损/报溢留痕） |

**采购 /api/admin/purchase**（场景 15.2/15.3）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/admin/purchase | 创建采购单（0 待审核） |
| GET | /api/admin/purchase/list | 采购单分页 |
| GET | /api/admin/purchase/{id} | 采购单详情 |
| PUT | /api/admin/purchase/{id}/audit | 审核（pass=true 通过 → 待收货；false 驳回 → 已取消） |
| PUT | /api/admin/purchase/{id}/cancel | 取消 |
| POST | /api/admin/purchase/receive | 分批入库（明细级收货，收满自动置已完成） |

**评价管理 /api/admin/comment**：GET /page（分页，支持商品名/状态筛选）· POST /reply（商家回复）· POST /status（隐藏/显示）。

**图片上传 /api/admin/upload**：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/admin/upload/image | 图片上传（本地文件存储默认；配置 `mall.product.oss.enabled=true` 后自动切换阿里云 OSS） |

## 6. mall-cart 购物车（Redis Hash 存储）

### 6.1 前台购物车（/api/cart）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/cart/list | 购物车列表（合并商品快照，失效商品 invalid=true 前端置灰） |
| POST | /api/cart/add | 加购 |
| POST | /api/cart/update | 修改数量 |
| POST | /api/cart/check | 批量勾选/取消勾选 |
| DELETE | /api/cart/remove | 删除条目 |
| GET | /api/cart/count | 购物车角标：件数合计 |

## 7. mall-order 订单中心

### 7.1 买家订单（/api/order）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/order/create | 下单（requestId 幂等，重复提交返回原订单） |
| POST | /api/order/{orderSn}/cancel | 取消订单（仅待付款） |
| POST | /api/order/{orderSn}/confirm-receive | 确认收货（仅待收货） |
| POST | /api/order/{orderSn}/pay | 拉起收银台：创建支付流水（1支付宝 2微信） |
| GET | /api/order/{orderSn} | 订单详情（订单头 + 明细 + 状态流水） |
| GET | /api/order/list | 我的订单分页（status：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款） |

### 7.2 后台订单（/api/admin/order）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/order/page | 订单分页（按订单号/状态筛选） |
| POST | /api/admin/order/deliver | 发货：1待发货 → 2待收货 + 物流信息 |

### 7.3 分库分表演示（/api/admin/order/sharding，阶段 8 13.5）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/order/sharding/demo | 分表路由演示（member_id 取模定位物理表 + 对应查询 SQL） |

## 8. mall-payment 支付中心

### 8.1 买家支付（/api/payment）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/payment/{paymentSn}/mock-callback | 模拟第三方支付回调（生产由支付宝/微信异步通知替换；重复回调幂等） |
| GET | /api/payment/query | 支付结果查询（收银台/结果页轮询；含查单兜底补偿回写） |
| POST | /api/payment/refund/apply | 申请退款（整单退款；仅退款/退货退款） |
| GET | /api/payment/refund/list | 我的退款单分页 |

### 8.2 后台退款审核（/api/admin/refund）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/refund/page | 退款单分页（status：0申请中 1审核通过 2退货中 3退款中 4已退款 5已拒绝） |
| POST | /api/admin/refund/{id}/audit | 审核退款申请（approved=true 通过 / false 拒绝） |
| POST | /api/admin/refund/{id}/confirm-return | 确认退货（退货退款：收到退货后执行退款） |
| POST | /api/admin/refund/{id}/retry | 重试执行退款（仅退款执行失败/超时后的补偿入口） |

## 9. mall-coupon 优惠券

### 9.1 前台优惠券（/api/coupon）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/coupon/center | 领券中心列表（进行中 + 未过期 + 未领完；附每人剩余可领数） |
| POST | /api/coupon/receive | 领券（SETNX 幂等 + 条件更新防超领） |
| GET | /api/coupon/mine | 我的优惠券（status：0未使用 1已锁定 2已使用 3已过期） |

### 9.2 后台模板管理（/api/admin/coupon）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/coupon/page | 模板分页（支持名称/状态筛选） |
| POST | /api/admin/coupon/save | 新增/修改模板 |
| POST | /api/admin/coupon/status | 模板状态：1进行中 0已结束 |

## 10. mall-seckill 秒杀

### 10.1 前台秒杀（/api/seckill）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/seckill/sessions | 场次列表（含进行中/未开始/已结束状态） |
| GET | /api/seckill/sessions/{sessionId}/products | 场次商品列表（预热后读缓存秒开，含剩余库存） |
| GET | /api/seckill/sessions/{sessionId}/rank | 秒杀排行榜 Top N（默认 10） |
| GET | /api/seckill/token | 获取秒杀幂等 token（进入秒杀页时调用，提交时携带） |
| POST | /api/seckill/submit | 秒杀提交（token 防重 + Lua 原子扣减 + MQ 削峰异步下单） |
| GET | /api/seckill/result | 下单结果轮询（status：0处理中 / 1成功含 orderSn / 2失败含 reason） |

### 10.2 后台管理（/api/admin/seckill）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/seckill/session/page | 场次分页（keyword 名称 / status 筛选） |
| POST | /api/admin/seckill/session/save | 保存场次（新增/修改） |
| POST | /api/admin/seckill/session/{id}/toggle | 启停场次（0禁用 1启用） |
| GET | /api/admin/seckill/product/page | 秒杀商品分页（按场次/状态筛选） |
| GET | /api/admin/seckill/product/list | 场次下启用商品列表（配置页下拉） |
| POST | /api/admin/seckill/product/save | 保存秒杀商品配置 |
| POST | /api/admin/seckill/product/{id}/toggle | 启停秒杀商品 |
| POST | /api/admin/seckill/product/{id}/delete | 删除秒杀商品配置（场次未开始才允许） |
| POST | /api/admin/seckill/session/{sessionId}/preheat | 手动预热场次（覆盖式，校验 seckill_stock ≤ sku.stock） |

## 11. mall-search 搜索（ES）

### 11.1 前台搜索（/api/search）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/search | 商品全文检索（高亮） |
| GET | /api/search/suggest | 搜索联想：前缀实时返回商品名称候选（前 8 条） |

### 11.2 搜索管理（/api/admin/search）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/admin/search/reindex | 全量重建商品索引（DB → ES bulk，返回索引文档数） |
| GET | /api/admin/search/index | 索引信息（存在性 + 文档数，ES 不可用时带 error 字段） |

## 12. mall-common 骨架验证（/api/common）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/common/ping | 存活探针：验证 Result 统一返回结构 |
| GET | /api/common/error | 异常链路：验证 GlobalExceptionHandler 统一兜底 |
| GET | /api/common/trace | traceId 链路：返回当前请求 traceId（MDC） |

## 13. 内部契约接口（/internal/**，仅服务间 Feign 调用，不对外暴露）

> 统一无 `/api` 前缀，经网关放行；跨服务调用契约定义在 mall-api 模块的 FeignClient。

**auth**（/internal/auth）：POST /internal/auth/check —— 令牌校验（签名/过期/黑名单，网关据此放行或拒绝）。

**member**（/internal/member）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /internal/member/register | 注册：创建买家账号 |
| POST | /internal/member/verify | 密码校验：登录用 |
| POST | /internal/member/update-password-by-phone | 按手机号修改密码：找回密码用 |
| POST | /internal/member/record-active | 登录成功记录在线 + 日活 |
| GET | /internal/member/stats/online | 实时在线人数（5 分钟窗口） |
| GET | /internal/member/stats/dau | 日活（date=yyyyMMdd） |
| GET | /internal/member/stats/checkin-today | 今日签到人数（看板） |
| GET | /internal/member/stats/new-members | 今日新增注册会员数（看板） |
| GET | /internal/member/stats/checkin-month | 指定会员当月签到天数（看板） |
| GET | /internal/member/stats/summary | 会员运营总览（看板聚合） |

**product**（/internal/product）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /internal/product/sku-info | 单个 SKU 下单快照（校验上下架/价格/库存） |
| GET | /internal/product/sku-infos | 批量 SKU 下单快照（cart/结算预览组装；下架也返回，由调用方标记失效） |
| POST | /internal/product/deduct-stock | 扣减库存（change_type：1下单 4秒杀；行锁校验防超卖） |
| GET | /internal/product/has-seckill-deducted | 是否存在秒杀扣减流水（关单回补判断） |
| GET | /internal/product/sales-rank | 商品销量排行榜（ZSET 倒序） |
| GET | /internal/product/stats/pv-uv | 商品 PV / UV（看板） |
| GET | /internal/product/stats/top-views | 商品浏览排行 Top N（看板） |
| GET | /internal/product/stats/warnings | 库存预警列表（看板） |
| POST | /internal/product/release-stock | 回补库存（change_type：2取消 3退款 9秒杀；按 bizSn+changeType 幂等） |
| POST | /internal/product/stock-in | 入库（change_type=6 退货入库；按 bizSn 幂等） |

**cart**（/internal/cart）：GET /internal/cart/checked-items/{memberId}（勾选条目，结算/下单用）· DELETE /internal/cart/checked/{memberId}（下单成功后清理已结算勾选条目）。

**order**（/internal/order）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /internal/order/info | 订单信息（payment 创建支付单/退款校验用） |
| GET | /internal/order/items | 订单项明细（退款联动组装消息体） |
| GET | /internal/order/comment-validate | 评价前校验订单项（存在性 + 归属 + 已完成） |
| POST | /internal/order/mark-paid | 支付成功回写：0→1（幂等） |
| POST | /internal/order/mark-refunded | 整单退款成功回写：1/2/3→5（幂等） |
| GET | /internal/order/stats/today | 今日订单概览（订单数/已支付销售额/秒杀订单数） |
| GET | /internal/order/stats/trend | 近 7 天订单趋势 |

**payment**（/internal/payment）：POST /internal/payment/create（创建支付单，幂等：同订单+同支付方式复用流水）· GET /internal/payment/by-order（按订单号查支付流水，结果页轮询用）。

**coupon**（/internal/coupon）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /internal/coupon/available | 我的可用券（未使用 + 未过期 + 门槛达标） |
| POST | /internal/coupon/lock | 锁券：0→1（下单占用，写 order_id；幂等） |
| POST | /internal/coupon/unlock | 退券：1→0（取消/超时关单回退；过期置 3） |
| POST | /internal/coupon/use | 核销：1→2（支付成功确认核销） |
| POST | /internal/coupon/refund | 退款退券：2→0（整单退款成功后；过期置 3） |

**seckill**（/internal/seckill）：GET /internal/seckill/verify-reservation（核验 Redis 预扣资格，防绕过秒杀入口）· POST /internal/seckill/release-seckill-stock（秒杀订单关单回补：活动进行中回补 Redis 秒杀库存，结束后回补 sku.stock change_type=9；幂等）。
