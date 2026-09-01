> 本文档为mall-practice 项目勘README 拆分出的专题说明，返回 [README](../README.md)。

### 业务表设计总览

`sql/mall.sql` 共 **29 张表**，表名前缀 = **数据语义域**（表装的是哪一域数据，而非被哪个平台使用）——多数域与模块同名（member_* 会员域归 mall-member、product_* 商品域归 mall-product）；例外有两个——admin_*（语义域 = 后台管理，管理员账号 + RBAC，由 mall-auth 认证权限服务持有）与 tx_message / mq_dead_letter（公共域组件表，归 mall-common，前缀取语义而非模块名）：

| 域 | 模块 | 表 | 支撑场景 |
|---|---|---|---|
| 后台管理域 | mall-auth（持有） | admin_user、admin_role、admin_menu、admin_user_role、admin_role_menu | 后台管理员账号 + RBAC 角色权限（菜单树：1目录 2菜单 3按钮；买家账号复用 member） |
| 会员域 | mall-member | member、member_address、member_point_log、member_favorite | 注册登录、收货地址、积分流水、收藏 |
| 商品域 | mall-product | product_category、product_brand、product_spu、product_sku、product_stock_log、product_comment | 分类/品牌/SPU（spu_code）/SKU（sku_code / low_stock 预警阈值）、库存流水对账（biz_sn + change_type 9 类；扣减/回补类流水生成列唯一索引 uk_biz_sku_type 防 MQ 重复投递）、商品评价（reply 商家回复） |
| 进销存域 | mall-product | product_supplier、product_purchase、product_purchase_item | 供应商档案、采购单（状态机 / 明细）、分批入库（与库存流水联动，归商品域同库） |
| 订单域 | mall-order | orders、order_item、order_status_log | 订单主表（幂等 request_id、类型 order_type、发货物流 delivery_company/delivery_sn）、快照明细、状态流转审计 |
| 支付域 | mall-payment | payment、payment_refund | 支付流水（回调幂等）、退款单（整单退款状态机：仅退款 / 退货退款两分支 + 退货物流 return_sn） |
| 营销域 | mall-coupon | coupon、coupon_user | 优惠券（发行总量/每人限领 per_limit）、领取/锁定/核销记录 |
| 秒杀域 | mall-seckill | seckill_session、seckill_product | 秒杀场次、秒杀商品（限购/秒杀价/秒杀库存） |
| 公共域（组件） | mall-common | tx_message、mq_dead_letter | 本地消息表（事务消息/最终一致性）+ 死信落库表（消费重试耗尽人工介入；表由使用消息的服务操作，如 order/payment，mall-common 本身不连 MySQL） |

无表模块：mall-cart（购物车纯 Redis Hash）、mall-search（ES 索引）、平台聚合层（gateway/admin/portal）；后台管理域 admin_* 五表由 mall-auth 持有。

**表与平台的数据边界**（前台商城 C 端 vs 管理后台 B 端，均不混用）：

| 边界类型 | 表 | 说明 |
|---|---|---|
| 后台专属（仅 B 端使用） | admin_* 五表 | 管理员账号 + RBAC 菜单权限，仅 mall-auth 读写；买家账号复用 member——两套账号体系彻底分离（登录入口 / 密码策略 / 数据模型不同，见场景 1.7） |
| 前台买家数据（C 端产生，B 端只读管理） | member、member_address、member_point_log、member_favorite | 注册登录 / 地址 / 积分流水 / 收藏均由买家产生；后台「会员管理 / 积分查询」仅查询或停用管理——同一对象两侧视图，非数据混用 |
| 跨平台共享业务数据（必须同源） | product_*、orders、order_item、order_status_log、payment、payment_refund、coupon、coupon_user、seckill_* | 前台下单、后台发货履约 / 售后审核是同一业务对象的两端操作（订单：买家创建 → 后台发货 → 买家收货），必须同一份数据；若按平台拆成两套表会双写不一致、订单对账断裂 |

> 表前缀为何不按平台命名：同一张表两平台都可能读写，前缀只能取一个，故取「数据语义域」而非「使用平台」；admin_ 五表虽是后台专属数据，但归属 mall-auth（认证权限服务）而非 mall-admin（聚合层不建表）；买家账号复用 member，故不存在 portal_ 前缀表。

**核心业务链路**：

1. **下单主链路**：下单（request_id 幂等）→ 锁定优惠券（coupon_user 状态→已锁定）→ 扣库存（SELECT FOR UPDATE 行锁 + stock_log 流水）→ 创建订单（orders + order_item 快照）→ 分布式事务（Seata AT）→ 支付
2. **支付链路**：支付回调（trade_no 幂等）→ 更新订单状态（order_status_log 记录流转）→ MQ 异步通知（发积分/短信等非核心动作；库存已在下单时行锁扣减，此处无需再动）
3. **超时关单**：RocketMQ 延迟消息 → 关单 → 回补库存（stock_log）→ 退回优惠券（coupon_user 已锁定→未使用）
4. **退款链路**（整单退款）：申请退款（payment_refund 创建，仅退款 / 退货退款两分支）→ 审核 → 第三方退款 → 回补库存 + 退回优惠券（退回时校验券有效期，过期置已过期）+ 订单状态→已退款；退货退款分支：买家寄回（return_company / return_sn 退货物流）→ 后台确认 → 退货入库（stock_log change_type=6）→ 再打款
5. **秒杀链路**：预热（Redis 预扣）→ Lua 原子扣减（含限购校验）→ MQ 削峰异步下单（orders.order_type=2）→ 异步扣 sku.stock（change_type=4）；秒杀订单超时关单回补：活动进行中回补 Redis 秒杀库存，活动已结束回补 sku.stock（change_type=9）
6. **履约与评价链路**：后台发货（orders.delivery_company / delivery_sn 物流 + delivery_time，1待发货→2待收货）→ 确认收货 / 超时自动收货（receive_time→3已完成）→ 评价（product_comment，唯一键防重复评价 + 后台回复）→ 积分返还（member_point_log）
7. **进销存链路**：采购单（product_purchase 状态机）→ 分批入库（sku.stock 增加 + stock_log change_type=5）→ 上架销售（下单扣减）→ 售后退货入库（change_type=6）+ 退款打款；盘点差异（change_type=7）调整留痕——库存从此有进有出，不靠「直接设库存」


### 两平台功能菜单总览

> 本项目共两个平台：**前台商城（C 端买家，mall-portal）** 与 **管理后台（B 端运营，mall-admin）**，职责边界：买家在商城逛、买、售后；运营在后台管商品、管库存、管采购、管订单履约、管营销、看数据。菜单按市面主流电商系统通用划分设计（参考市面电商后台的商品中心 / 订单中心 / 采购中心 / 库存中心 / 促销中心 / 系统管理结构，以及 ERP 进销存的供应商 / 采购入库 / 退货入库链路），每条目标注对应「电商技术场景清单」功能点编号，保证菜单与功能点一一对应、两平台不交叉。

#### 管理后台（mall-admin）

> 菜单树即 admin_menu 表初始化数据（RBAC 权限粒度到菜单 / 按钮，与 mall.sql 种子完全一致）；页面由后台前端工程渲染，管理接口由前端经网关直连对应业务服务（mall-admin 仅聚合数据看板）。注：会员管理 / 积分管理 / 数据统计暂未单列页面（会员数据由 member 服务维护、看板已聚合到「数据看板」单页，后续可按需扩菜单）；「入库管理」不是独立页面——分批收货入库是采购单页面的「分批入库」按钮操作（product:purchase:receive）。

| 一级菜单 | 二级菜单 | 页面功能 | 对应功能点 |
|---|---|---|---|
| 数据看板 | — | 今日订单数 / 销售额 / 新增会员 / 库存预警数概览（DashboardService 聚合） | 10.4、5.5 |
| 商品管理 | 分类管理 | 分类树维护 | 2.1 |
| | 品牌管理 | 品牌增删改 | 2.1 |
| | 商品管理 | 商品列表 / 编辑（SPU+SKU）/ 图片上传 / 上下架 | 2.2、2.3、2.6 |
| | 供应商管理 | 供应商档案 / 停用 | 15.1 |
| | 采购管理 | 创建采购单 / 审核 / 取消 / 分批收货入库（入库不单列，为采购页「分批入库」按钮操作） | 15.2、15.3 |
| | 库存管理 | 实时库存查询 / 库存流水查询 / 盘点调整 / 库存预警 | 5.1、5.4、15.4、5.5 |
| | 评价管理 | 评价审核 / 回复 / 隐藏 | 2.8 |
| 营销管理 | 优惠券模板 | 券模板创建 / 发行 / 启停 | 4.1 |
| 交易管理 | 订单管理 | 订单列表 / 详情（含支付流水）/ 发货 | 6.4、6.8 |
| | 退款管理 | 退款 / 退货审核（确认退货 → 退货入库联动） | 7.9、15.5 |
| 秒杀管理 | 秒杀场次 | 场次管理 / 库存预热 | 14.1、14.3 |
| | 秒杀商品 | 秒杀商品配置 | 14.2 |
| 系统管理 | 用户管理 | 后台账号增删改 / 重置密码 / 分配角色 | 1.7 |
| | 角色管理 | 角色 + 权限分配 | 1.8、1.9 |
| | 菜单管理 | 菜单 / 按钮权限维护 | 1.8 |

#### 前台商城（mall-portal）

| 一级频道 | 页面 | 页面功能 | 对应功能点 |
|---|---|---|---|
| 首页 | 首页 | 分类导航 / 推荐商品 | 2.3 |
| 商品频道 | 商品列表页 | 分类筛选 / 排序 | 2.3 |
| | 商品详情页 | 详情 / 加购 / 收藏 / 点赞 / 秒杀入口 | 2.3、2.4、2.7、10.5 |
| | 搜索页 | ES 搜索 / 联想 / 高亮 | 13.2 |
| 购物车 | 购物车页 | 加购 / 改数量 / 勾选结算 | 3.1～3.5 |
| 交易频道 | 结算页 | 地址 / 选券 / 优惠计算 / 提交订单 | 3.5、4.7、6.1 |
| | 收银台 | 拉起支付 / 模拟支付 | 7.1、7.2 |
| | 支付结果页 | 成功 / 失败结果 | 7.4 |
| 订单中心 | 订单列表 | 全部状态 tab / 取消 / 付款 | 6.4、6.5 |
| | 订单详情 | 物流 / 状态轨迹 / 确认收货 | 6.4、6.8、6.9 |
| | 评价页 | 打分 / 图文评价 | 2.8 |
| | 退款 / 退货页 | 申请退款（仅退款 / 退货退款）/ 填写退货物流 | 7.8、15.5 |
| 会员中心 | 登录 / 注册 / 找回 | 图形验证码 / 短信 | 1.1、1.10、12.5 |
| | 个人资料 | 头像 / 昵称 | 1.4 |
| | 收货地址 | 地址增删改 | 1.6 |
| | 我的收藏 | 收藏列表 | 2.7 |
| | 浏览足迹 | 最近浏览 50 条 | 10.6 |
| | 我的积分 | 余额 / 流水 / 签到 | 1.11、10.3 |
| | 我的优惠券 | 可用 / 已用 / 过期 | 4.2～4.6 |
| 秒杀频道 | 秒杀会场 | 场次切换 / 秒杀下单 / 结果查询 | 14.1、14.4、14.5、14.6 |

**两平台边界**：前台只做买货相关（浏览 / 加购 / 下单 / 售后申请 / 个人资产），无任何管理动作；后台只做运营管理（商品 / 库存 / 采购 / 订单履约 / 售后审核 / 营销 / 数据 / 系统），无购物车 / 收藏等买家行为。同一业务对象两侧视图不同（如库存：前台只读剩余量，后台可查可盘可入）。

**功能点全覆盖说明**：109 个功能点按「是否有用户界面」分两类——**59 个页面级功能点**（浏览 / 下单 / 管理操作等）已在上方两表逐条映射到菜单 / 页面，全覆盖无遗漏；**50 个系统级技术点**无独立页面入口属正常设计（如 8.x MQ 消息、9.x Redis、11.x 数据库、12.x 高并发与工程横切面、13.x 架构进阶、14.x 秒杀内部链路等），它们以「页面功能背后的实现」形式落地（例：8.2 延迟消息关单支撑订单列表的自动关闭、9.2 缓存三防支撑商品详情页的高并发读、12.3 幂等 token 支撑结算页防重复下单），验收时按对应场景清单逐项验证即可。


### 电商技术场景清单

> 覆盖近两年电商高频技术场景，15 个场景共 109 个业务功能点，以表格形式总览——功能点逐项编号，技术方案就近写入对应单元格，一眼看清每个场景「做什么 + 用什么技术」。

| 场景（模块） | 业务功能点 | 技术点（含落地表） |
| --- | --- | --- |
| **1. 用户模块**（mall-member / mall-auth） | 1.1 买家注册 / 登录<br>1.2 JWT 签发 / 刷新<br>1.3 网关 JWT 鉴权<br>1.4 个人资料修改<br>1.5 会员等级权益<br>1.6 收货地址管理<br>1.7 后台管理员登录<br>1.8 RBAC 权限管理<br>1.9 接口权限校验<br>1.10 修改 / 找回密码<br>1.11 积分查询与流水 | 1.1 BCrypt 加密（加盐 / 慢哈希，不用 MD5）；买家登录：portal→auth 签发 JWT，auth 经 HTTP 调 member 内部校验接口核对密码（member 表数据归属不动）<br>1.2 JWT 无状态 vs 无法主动失效 → Redis 黑名单 + refresh 轮换防重放 + 用户令牌跟踪集（禁用/重置密码/角色变更踢下线即时生效；auth 查 Redis 校验，网关经 WebClient 调 auth 透传结果；gateway 无 Redis 依赖故不自查）<br>1.3 网关鉴权 vs 业务服务鉴权区别；业务服务信任网关透传的 X-User-Id 等头（生产需网络隔离，禁止业务端口对外暴露）；网关对 /api/admin/** 额外校验 userType=ADMIN（买家 token 返回 403），业务服务侧 mall-common AdminApiAuthFilter 对 /api/admin/* 二次校验 X-User-Type=ADMIN（双层防护，防内网直连绕过网关）<br>1.5 member.level：折扣 / 免运费 / 积分倍率；买家侧"权限"= 账号状态（禁用 / 拉黑）+ 等级权益，为什么不用 RBAC（扁平权益 vs 树形权限）<br>1.7 前后台账号分离：人员属性 / 密码策略 / 登录入口不同（member 状态+等级权益模型 vs admin_user RBAC 权限模型）<br>1.8 RBAC 五表（用户-角色-菜单），权限粒度到按钮<br>1.9 @PreAuthorize 校验 perms<br>1.10 图形 + 短信验证码（模拟短信，Redis 存码 + 过期）<br>1.11 member.points 余额 + member_point_log 流水（支付返积分 / 退款扣回）<br>**表**：member（level / points）、member_address、member_point_log、admin_user / admin_role / admin_menu / admin_user_role / admin_role_menu |
| **2. 商品模块**（mall-product） | 2.1 商品分类 / 品牌管理<br>2.2 SPU / SKU 模型维护<br>2.3 商品列表 / 详情查询<br>2.4 商品详情 Redis 缓存<br>2.5 缓存预热<br>2.6 商品图片上传<br>2.7 商品收藏 / 取消收藏<br>2.8 商品评价（打分 / 图文，确认收货后） | 2.1 分类树<br>2.2 规格、价格、上下架；SPU/SKU 模型设计（核心）<br>2.4 穿透（布隆过滤器 / 缓存空值）；击穿（互斥锁 / 逻辑过期）；雪崩（TTL 随机偏移）<br>DB 与 Redis 双写一致性（先更 DB 再删缓存 / 延迟双删 / Canal）；热点 key 高并发读<br>2.5 热销 Top N 缓存预热（xxl-job productPreload 执行器任务 + @Scheduled 本地双通道兜底 + 手动触发接口）<br>2.6 配置驱动双通道：mall.product.oss.enabled=true 走阿里云 OSS（启动 fail-fast 校验必填配置），未配置默认本地存储；UploadStorage 抽象按 @Order 选通道，接入 OBS 等其他对象存储仅需新增实现类<br>2.7 member_favorite 收藏列表（member_id + spu_id 唯一防重复）<br>2.8 product_comment 评价（uk_order_item_id 唯一键防重复评价；后台审核 / 回复 reply / 隐藏）<br>**表**：product_category / product_brand / product_spu / product_sku、member_favorite、product_comment |
| **3. 购物车模块**（mall-cart） | 3.1 加入购物车<br>3.2 修改数量 / 删除条目 / 勾选结算<br>3.3 购物车列表查询<br>3.4 下单成功后清理已结算条目<br>3.5 结算前校验（下架 / 库存 / 价格变更） | 3.1 Redis Hash：key=cart:{memberId}，field=skuId<br>购物车为什么放 Redis（读写频繁 / 非强一致）；学习项目购物车不持久化（Redis 故障丢购物车可接受，DB 同步方案为可选扩展）<br>3.5 失效条目标记 + 结算时提示，避免下单时才报错<br>**表**：无（纯 Redis） |
| **4. 优惠券模块**（mall-coupon） | 4.1 券模板创建 / 发行<br>4.2 用户领券<br>4.3 下单锁券<br>4.4 支付成功核销<br>4.5 取消订单 / 退款退回<br>4.6 过期作废<br>4.7 下单优惠计算（满减 / 折扣） | 4.1 总量 total_count、每人限领 per_limit<br>4.2 防超领：Redisson 分布式锁（RLock，成员+券维度，看门狗续期 / 可重入，事务提交后释放）+ DB 条件更新兜底（received_count < total_count）；领取幂等：锁内查 coupon_user（member_id + coupon_id）判重（per_limit 可 >1，无法唯一键兜底）<br>4.3～4.5 coupon_user 状态机：未使用→已锁定→已使用，取消 / 退款退回→未使用（退回时校验券有效期，已过期则置已过期）<br>4.6 Redis 过期 key + xxl-job couponExpireScan 任务兜底<br>Redisson：可重入 / 锁续期 / 锁失效<br>4.7 按 threshold 满减门槛 / amount 折扣率计算优惠金额；全场券（无品类 / 单品维度，简化设计）<br>**表**：coupon（per_limit）、coupon_user（0未使用 1已锁定 2已使用 3已过期） |
| **5. 库存模块**（mall-product）【核心】 | 5.1 库存查询<br>5.2 下单扣库存<br>5.3 取消订单 / 超时关单回补库存<br>5.4 库存流水记录<br>5.5 库存预警 | 5.2 超卖三方案全部落地：下单扣库存 = SELECT FOR UPDATE 行锁（悲观锁）+ stock_log 流水；盘点调整 = @Version 乐观锁（version 条件更新，低并发写）；入库 / 回补 = setSql 原子自增（stock = stock + n）；扣减失败 Seata 全局事务回滚（order 下单编排）<br>5.3 延迟消息释放库存<br>5.4 stock_log 每笔 before / after + change_type 9 类（1下单扣减 2取消回补 3退款回补 4秒杀扣减 5采购入库 6退货入库 7盘点调整 8人工调整 9秒杀回补）+ biz_sn 业务单号，可对账；change_count 统一“正数增加、负数减少”（入库为正、扣减为负）；扣减/回补类流水（1/2/3/4/6/9）以生成列 idem_key（biz_sn:sku_id:change_type）建唯一索引，MQ 至少一次投递下重复消费靠唯一键拦截（DuplicateKeyException 幂等跳过，事务回滚重复变更；采购入库 5 / 盘点 7 可重复不参与约束）<br>5.5 sku.low_stock 阈值（低于即预警，NULL 取全局默认）→ 通知运营联动补货<br>为什么会超卖：check-then-act 非原子；乐观锁优缺点（无锁等待 vs ABA / 重试风暴）<br>**表**：product_sku（version 乐观锁）、product_stock_log（流水对账） |
| **6. 订单模块**（mall-order）【电商核心】 | 6.1 创建订单<br>6.2 订单明细快照<br>6.3 订单状态机流转<br>6.4 订单列表 / 详情查询<br>6.5 取消订单<br>6.6 超时关单<br>6.7 大流量接口防刷<br>6.8 后台发货<br>6.9 确认收货 / 超时自动收货 | 6.1 下单幂等：request_id 唯一索引 + 前端 token；雪花算法订单号（时间回拨：回拨等待 / 备用生成器）<br>6.2 order_item 保存下单时价格 / 名称<br>6.3 6 状态（0待付款 1待发货 2待收货 3已完成 4已取消 5已退款）+ order_status_log 审计防乱改<br>6.5 回补库存 + 退回优惠券<br>6.6 RocketMQ 延迟消息（30 分钟未支付自动关闭，释放库存 + 退回券）<br>6.7 Sentinel；订单分库分表（按 member_id 哈希，ShardingSphere；注意分表后 uk_request_id / uk_order_sn 唯一索引失效，按订单号查询需 member_id 路由，学习项目逻辑分表演示）<br>6.8 后台发货：orders.delivery_company / delivery_sn 物流单号 + delivery_time 发货时间（1待发货→2待收货）<br>6.9 orders.receive_time 记录收货时间（2待收货→3已完成）；超时自动收货（延迟消息 / xxl-job 扫描）<br>**表**：orders（request_id / order_type）、order_item（快照）、order_status_log |
| **7. 支付与退款模块**（mall-payment）【核心】 | 7.1 拉起收银台<br>7.2 模拟第三方支付<br>7.3 支付回调接收<br>7.4 回调更新订单状态<br>7.5 支付结果 MQ 异步通知<br>7.6 支付单状态机<br>7.7 回调丢失主动查单<br>7.8 申请退款（仅退款 / 退货退款）<br>7.9 退款审核<br>7.10 调用第三方退款<br>7.11 退款成功联动<br>7.12 MQ 异步通知业务更新 | 7.1 生成支付单 / 支付参数<br>7.2 支付宝 / 微信渠道<br>7.3 回调幂等：trade_no 唯一 + 状态前置校验 + 加锁；回调接口不能耗时（第三方重试机制 / 超时）→ 耗时操作 MQ 异步<br>7.4 order_status_log 记录流转<br>7.5 发积分 / 短信；消息可靠性<br>7.6 payment.status：0待支付 1成功 2失败 3已退款<br>7.7 定时扫描待支付单 → 第三方查单兜底<br>7.8 payment_refund 退款状态机（0申请中 1审核通过 2退货中 3退款中 4已退款 5已拒绝；仅退款跳过 2）；refund_type：1仅退款 2退货退款（整单退款，refund_amount=订单实付）<br>7.10 整单退款；退款幂等<br>7.11 回补库存 + 退回优惠券 + 订单状态→已退款；退货退款分支：买家寄回（return_sn）→ 后台确认收货 → 退货入库 → 再打款<br>**表**：payment（trade_no 唯一）、payment_refund（refund_type / return_sn）、product_stock_log（回补 / 退货入库流水）、coupon_user（已使用→未使用） |
| **8. MQ 消息场景**（RocketMQ）【高频，坑全部复现】 | 8.1 支付结果通知（PAY→MEMBER 发积分；退款四路联动 PAY→ORDER/PRODUCT/COUPON/MEMBER）<br>8.2 延迟消息超时关单（ORDER→ORDER）<br>8.3 秒杀削峰异步下单（SECKILL→ORDER）<br>8.4 本地消息表 tx_message<br>8.5 死信队列与重试 | 消息丢失：生产者确认 / 刷盘 / 消费 ACK 重试<br>重复消费：业务幂等（数据库唯一索引）<br>消息积压：消费扩容 + 临时 topic 转发<br>延迟消息：18 个延迟级别<br>事务消息：半消息 + 回查（本地事务与消息原子性）<br>8.5 消费失败重试 N 次仍失败 → 进 DLQ 死信队列（人工介入 / 补偿，避免无限重试阻塞消费）<br>**表**：tx_message（本地消息表：biz_id 唯一幂等、重试次数）、mq_dead_letter（死信落库：消费组 / 主题 / 异常 / 消息体截断，人工介入） |
| **9. Redis 高频场景** | 9.1 Redisson 分布式锁<br>9.2 缓存穿透 / 击穿 / 雪崩防护<br>9.3 热点 key 高并发读<br>9.4 Hash 购物车存储<br>9.5 缓存预热<br>9.6 Lua 脚本原子扣减<br>9.7 Redis 过期策略应用 | 9.1 领券防超领（Redisson RLock）；扣库存三方案对照（行锁 / 乐观锁 / 原子自增）；分布式锁实现与锁失效<br>9.2 商品详情<br>9.5 xxl-job productPreload 任务<br>9.6 秒杀库存；Lua 原子性<br>9.7 券过期 / 在线心跳清理；过期删除策略（惰性 + 定期）<br>Redis 持久化 RDB / AOF<br>**表**：无（纯 Redis） |
| **10. 数据统计场景**（在线人数 / UV / 签到 / 排行榜） | 10.1 实时在线人数<br>10.2 商品 PV / UV 统计<br>10.3 会员签到 / 日活<br>10.4 销量 / 秒杀排行榜<br>10.5 点赞<br>10.6 浏览足迹 | 10.1 ZSET 滑动窗口：`ZADD online_users <时间戳> <用户ID>`（请求刷新心跳），`ZCOUNT online_users (now-5min) +inf` 统计 5 分钟在线，`ZREMRANGEBYSCORE` 清理离线；另一做法 Bitmap（SETBIT + BITCOUNT，适合 UV/DAU 去重）<br>10.2 PV：`INCR page:view:{spuId}`；UV：HyperLogLog `PFADD/PFCOUNT`（12KB 亿级 UV，误差 0.81%，去重非精确）<br>10.3 Bitmap：`SETBIT sign:{memberId}:{yyyyMM} <day> 1`，`BITCOUNT` 当月天数，`BITFIELD` 连续签到<br>10.4 ZSET：`ZINCRBY rank:sales 1 skuId`，`ZREVRANGE` Top N（本质排序树）<br>10.5 Set：`SADD/SREM/SCARD` + `SISMEMBER` 判点过（天然幂等）<br>10.6 ZSET：`ZADD history:{memberId} <时间戳> <spuId>` 记录足迹，`ZREVRANGE` 最近浏览 + `ZREMRANGEBYRANK` 截断 50 条<br>为什么不用 MySQL 计数（行锁热点 / 写放大），Redis 计数器异步落库（销量回写 product_sku.sale_count）<br>**表**：纯 Redis 无新表，需持久化的计数异步落 product_sku.sale_count / product_spu.sales |
| **11. 数据库高频场景** | 11.1 索引设计落地<br>11.2 慢 SQL 定位<br>11.3 并发控制方案对比<br>11.4 事务隔离级别演示<br>11.5 大表分页优化 | 11.1 幂等唯一键 uk_request_id / uk_trade_no / uk_order_item_id / uk_biz_id / uk_biz_sku_type（product_stock_log 生成列唯一键，防重复流水）；业务编码唯一键 uk_spu_code / uk_sku_code / uk_purchase_sn；扫描组合索引 orders(status,create_time) / tx_message(status)；查询索引 member_id / spu_id / sku_id / order_id / status（coupon、seckill_session 后台列表）<br>11.2 explain 分析<br>11.3 扣库存三方案对照：select for update 行锁（下单扣减）/ version 乐观锁（盘点）/ setSql 原子自增（入库回补）<br>11.4 幻读 / 不可重复读<br>11.5 延迟关联<br>**表**：全业务表索引设计 |
| **12. 高并发、安全与工程横切面** | 12.1 Sentinel 接口限流<br>12.2 接口防刷<br>12.3 幂等 token<br>12.4 Jmeter 压测复现超卖<br>12.5 图形 / 滑块验证码<br>12.6 全局异常处理器<br>12.7 统一返回封装<br>12.8 参数校验<br>12.9 链路 traceId<br>12.10 ID 生成器<br>12.11 接口文档 | 12.1 接口限流 + 热点参数限流<br>12.2 Redis 用户访问频率计数<br>12.3 防重复请求<br>12.4 验证行锁 / 乐观锁 / 原子自增三方案<br>12.5 登录注册防机器（Redis 存验证码 + 限时）<br>12.6 统一捕获业务异常返回 JSON<br>12.7 Result&lt;T&gt;<br>12.8 JSR-303 @Valid（分组校验）<br>12.9 SLF4J + Logback + MDC（日志链路追踪）<br>12.10 雪花算法<br>12.11 springdoc-openapi（各服务 doc.html 在线调试，与「快速开始」验证入口一致） |
| **13. 架构进阶与性能优化** | 13.1 Canal 同步缓存<br>13.2 ES 商品搜索<br>13.3 Caffeine 多级缓存<br>13.4 网关层限流鉴权<br>13.5 订单分库分表<br>13.6 SkyWalking 链路排查 | 13.1 监听 MySQL binlog（canal-server 1.1.7 编排 + instance.properties + mall-search CanalSyncService 消费端）<br>13.2 分词 / 高亮<br>13.3 本地缓存多级缓存<br>13.4 RequestRateLimiter 令牌桶限流（秒杀路由 20/s、登录路由 10/s，Redis 计数）+ AuthGlobalFilter JWT 鉴权与 /api/admin/** 角色校验<br>13.5 ShardingSphere<br>13.6 排查慢调用 |
| **14. 秒杀场景**（mall-seckill） | 14.1 场次管理<br>14.2 秒杀商品配置<br>14.3 库存预热<br>14.4 秒杀下单（Lua 扣减 + 限购）<br>14.5 MQ 削峰异步下单<br>14.6 秒杀结果查询 | 14.1 seckill_session 场次（时间 / 状态）<br>14.2 seckill_product：seckill_price 秒杀价 / seckill_stock 秒杀库存 / limit_per_user 每人限购<br>14.3 活动开始前秒杀库存同步预热到 Redis（配置校验 seckill_stock ≤ sku.stock）<br>14.4 Lua 原子扣减 + 限购校验（防超卖 / 防黄牛；限购计数 seckill:limit:{pid}:{memberId} 按每人维度隔离，预扣标记 seckill:reserved:{pid}:{memberId} 同维度；存 Redis 无 DB 持久化——学习项目可接受，Redis 故障限购失效）<br>14.5 前端快速失败 → MQ 削峰 → 异步创建订单（orders.order_type=2；落单前 order 调 seckill 核验 Redis 预扣资格（Feign 默认 / Dubbo 可选，双通道），防绕过秒杀入口直接下单）→ 异步扣 sku.stock（change_type=4）<br>14.6 下单结果轮询 / 通知；秒杀订单超时关单：活动进行中回补 Redis 秒杀库存，活动已结束回补 sku.stock（change_type=9）<br>**表**：seckill_session、seckill_product、orders（order_type=2） |
| **15. 进销存场景**（mall-product） | 15.1 供应商管理<br>15.2 采购单创建 / 审核<br>15.3 采购入库（分批收货）<br>15.4 库存盘点 / 调整<br>15.5 退货入库<br>15.6 出入库流水对账 | 15.1 product_supplier 供应商档案（联系人 / 电话 / 状态，停用不可下采购单）<br>15.2 product_purchase 状态机（0待审核 1待收货 2部分入库 3已完成 4已取消）+ product_purchase_item 明细（采购价 / 数量 / 已入库数）<br>15.3 分批入库：received_quantity 累计 ≤ quantity，入库事务 = sku.stock 增加 + stock_log 留痕（change_type=5）；库存预警联动 5.5 触发补货<br>15.4 盘点差异调整 stock（@Version 乐观锁条件更新）+ 流水留痕（change_type=7，报损 / 报溢）<br>15.5 退款需退货 → 买家寄回 → 后台确认收货 → 退货入库（change_type=6）+ 第三方退款打款<br>15.6 stock_log 按 change_type / biz_sn 聚合对账（进货-销售-退货闭环）<br>为什么先采购入库再上架：销售库存的来源，避免「无货源直接设库存」的空中楼阁<br>**表**：product_supplier、product_purchase、product_purchase_item、product_stock_log（biz_sn / change_type） |
