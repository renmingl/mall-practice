> 本文档为 mall-practice 项目 README 拆分出的专题说明，返回 [README](../README.md)。

## 工程结构（模块架构）

16 个后端模块按「平台 / 层次」分四类，另有 2 个 npm 前端模块（mall-web-admin / mall-web-portal，独立部署；工程结构树见「系统架构」图 4，编译期 / 运行时依赖关系见「系统架构」图 5）：

**① 前端平台（聚合层，无表不落库）**

| 模块 | 平台定位 | 职责 |
|---|---|---|
| mall-admin | 管理后台平台（B 端运营） | 数据看板聚合：DashboardService 经 mall-api Feign 聚合订单/会员/商品服务看板数据（今日订单数/销售额/新增会员/库存预警，仅 4 个文件）；商品/采购/库存/订单/售后/营销/系统等管理接口由前端经网关直连对应业务服务（路由按服务路径分流，无需聚合层中转），自身无表无数据库 |
| mall-portal | 前台商城平台（C 端买家） | 买家侧聚合：首页、商品详情、购物车、下单流程编排，自身无表无数据库 |

**② 统一入口与认证（两平台共用）**

| 模块 | 职责 |
|---|---|
| mall-gateway | 统一入口：路由、鉴权、限流、跨域 |
| mall-auth | 认证中心：前后台账号认证（买家复用 member + 后台 admin_user）、JWT 签发 / 校验、RBAC 角色权限（admin_* 五表） |

**③ 业务服务（数据归属，供两平台共用）**

| 模块 | 职责 |
|---|---|
| mall-member | 会员信息、收货地址、积分 |
| mall-product | 商品、分类、品牌、库存、供应商 / 采购（进销存） |
| mall-cart | 购物车（Redis 存储） |
| mall-order | 订单、关单延迟消息 |
| mall-payment | 支付对接、支付回调、退款 |
| mall-coupon | 优惠券发放与核销 |
| mall-seckill | 秒杀活动（Redis 预扣 + 限流 + 削峰） |
| mall-search | 商品搜索（ES 索引与检索） |

**④ 基础与契约模块**

| 模块 | 职责 |
|---|---|
| mall-common | 统一返回结构（Result<T>）、全局异常、工具类、雪花 ID、MDC traceId 工具、Logback 日志配置、Redis 配置、后台管理接口权限过滤器（AdminApiAuthFilter：/api/admin/* 校验 X-User-Type=ADMIN，与网关双层防护）；MQ 封装（MqSender / TxMessageService / DeadLetterService）、xxl-job 执行器封装（XxlJobAutoConfiguration）、Redisson 分布式锁封装（RedissonAutoConfiguration）均已落地（条件装配：配置/依赖满足才生效）；文件存储抽象随阶段 3 落地在 mall-product（UploadStorage 接口 + 本地/OSS 双通道，接入 OBS 等仅需新增实现类，见「技术栈 → 阿里云 OSS」） |
| mall-mbg | MyBatis-Plus Generator 代码生成，产出实体类与 Mapper（mall 库 29 表 entity/mapper/xml 已生成） |
| mall-api / mall-dubbo-api | 服务间调用接口契约，Feign 与 Dubbo 各自独立定义（mall-api 已内置 openfeign 依赖，10 个服务依赖；mall-dubbo-api 秒杀契约 SeckillDubboService，order 调用 / seckill 实现） |

> **「平台 ≠ 服务」辨析**：mall-admin / mall-portal 是平台聚合层（只管页面数据组装与流程编排，无表）；mall-product / mall-order 等是业务数据服务（拥有表，被两个平台共同调用）——mall-product 不是「管理后台」，mall-admin 也不是「数据服务」。表名前缀按数据语义域命名（product_* 商品域归 mall-product、admin_* 后台管理域归 mall-auth 持有）：admin_* 是后台账号权限数据，由认证权限服务管而非聚合层建表；买家账号复用 member，故不存在 portal_ 前缀表（平台数据边界见业务篇「业务表设计总览」）。

> 阶段 1 说明：12 个服务模块已含启动类 + application.yml（可直接启动并注册 Nacos）+ 骨架验证接口（/api/common/ping|error|trace）；mall-common 已实现统一返回/全局异常/雪花 ID/traceId 工具/日志配置；mall-mbg 已生成 29 表实体与 Mapper（7 个有表服务已接入）；全部业务代码（Service/Controller）按业务篇「电商技术场景清单」逐场景实现，依赖引入时机见「技术栈 → 依赖引入状态」小节。
>
> 阶段 2 说明：双账号体系已闭环——买家注册 / 登录（图形 + 模拟短信验证码、BCrypt、JWT 双令牌 + Redis 黑名单 + refresh 轮换）、找回密码（前台找回密码页 + 重置密码页均已交付）、收货地址 / 个人资料 / 积分查询；后台 admin 登录 + RBAC 五表（用户 / 角色 / 菜单）+ 按钮级 @PreAuthorize；网关集中鉴权（透传 X-User-Id / X-User-Type / X-User-Perms，/api/admin/** 校验 userType=ADMIN 角色分流，非 ADMIN 返回 403）+ 业务服务侧 AdminApiAuthFilter 二次兜底；管理动作即时生效：禁用 / 删除 / 重置密码 / 角色权限变更均触发用户全部令牌失效（踢下线）。后台管理接口由 mall-auth 直接提供（admin_* 数据归属 auth），商品/库存/订单等管理接口由前端经网关直连各业务服务，mall-admin 聚合层负责数据看板（DashboardService 经 Feign 聚合），不中转业务管理请求。
>
> 阶段 3 说明：商品域与进销存已闭环——分类 / 品牌（分类最多三级、父子约束校验）；SPU/SKU 模型（spu_code / sku_code 唯一，上架需至少一个启用 SKU）；供应商档案 + 采购单状态机（0待审核 1待收货 2部分入库 3已完成 4已取消）+ 分批入库（库存流水联动 change_type=5）；盘点调整（change_type=7）与库存预警（low_stock 阈值）；商品详情 Redis 缓存三防（穿透空值短缓存 / 击穿 SETNX 互斥锁 / 雪崩 TTL 随机偏移）+ 热销 Top N 定时预热（xxl-job productPreload 任务 + @Scheduled 本地兜底双通道）；收藏（member_favorite 唯一防重复）；图片上传双通道（UploadStorage 抽象：`mall.product.oss.enabled=true` 走阿里云 OSS，未配置默认本地存储，上传接口与静态访问映射已落地）。
>
> 阶段 4/5/6 说明（购物车·营销 / 交易核心 / 支付履约，**已完成**，代码全部落地，三阶段集成验证已全部通过——验证脚本为作者本机维护未入库）：
>
> **购物车与营销**：mall-cart 购物车 Redis Hash 存储（key=cart:{memberId}，field=skuId，value=JSON {quantity, checked}，无 DB 依赖），列表组装调 product 拉 SKU 快照（价格/上下架/库存），失效商品标 invalid 前端置灰，结算前再次校验；mall-coupon 券模板（后台新增/修改/启停）+ 领券（Redisson 分布式锁防重复提交 + DB 条件更新防超领 + per_limit 限领）+ 锁券（下单时锁定，coupon_user.order_id 为关联键）/ 核销（支付成功）/ 退回（关单/退款，按 orderId+memberId 条件更新幂等）+ 过期扫描（xxl-job couponExpireScan + @Scheduled 双通道把过期未用券置为失效）+ 优惠计算（满减/折扣，下单选券时校验门槛与有效期）。
>
> **交易核心**：mall-order 下单编排 @GlobalTransactional（Seata AT，product 扣库存 / coupon 锁券为参与方）：requestId 幂等（uk_request_id 唯一索引）→ 购物车勾选条目 → SKU 快照校验（状态/库存）→ 锁券 → 扣库存（SELECT FOR UPDATE 行锁 + stock_log 流水）→ 建订单 + 明细快照 + 状态流水 → 清购物车；订单状态机 0待付款→1待发货→2待收货→3已完成，0→4已取消，1/2/3→5已退款（每次流转条件更新幂等 + order_status_log 审计）；取消订单（仅待付款）回补库存/退券；RocketMQ 延迟消息关单（30 分钟，DELAY_LEVEL_30M；状态更新 0→4 与回补同事务，补偿失败回滚状态可重试）+ 定时任务兜底扫描（xxl-job orderCloseScan / orderAutoReceive + @Scheduled 双通道）；支付/退款联动：mall-payment 创建支付单（幂等复用）→ 模拟回调（trade_no 幂等，0→1 条件更新）→ Feign markPaid（订单 0→1 + 核销券）→ 本地消息表 tx_message（事务提交后发送）投递发积分（member 消费，按等级倍率返积分 + member_point_log 幂等）；查单兜底任务补偿（xxl-job paymentWriteBack，支付成功但订单未标记）；退款状态机（仅退款审核通过即退 / 退货退款需确认退货），退款成功发四路 MQ（回补库存 change_type=3 / 退券 / 扣回积分 / 订单标已退款 5），本地消息表 resendPending 定时补发（xxl-job paymentResend）、重试超上限进 DLQ。
>
> **支付与履约**：收银台拉起（payOrder 创建支付流水）→ 模拟回调（演示环境：前端点击立即支付即模拟第三方回调成功）→ 支付结果页轮询查单；后台订单管理（分页 + 发货 1→2 + 物流信息）；确认收货（2→3）+ 超时自动收货定时任务；收货后评价（orderItemId 唯一防重复，商家回复/隐藏显）；退款申请（仅退款/退货退款，退货需填物流）→ 后台审核（通过/拒绝）/ 确认退货；后台退款单分页 + 评价管理分页。前端：portal 新增购物车/领券中心/我的优惠券/结算确认（预览聚合 mall-portal CheckoutService）/订单列表/详情/收银台/支付结果/退款申请/退款列表/评价 11 个页面；admin 新增券模板/订单管理/退款审核/评价管理 4 个页面（菜单种子与新增表均已直接并入 sql/mall.sql，无存量升级脚本）。

## 服务间通信

**最终形态：全 Feign + order↔seckill Dubbo 双通道（已落地）**

- **同步链路（OpenFeign）**：服务间调用统一走 HTTP Feign，契约定义在 `mall-api`——下单编排（order → product 扣库存 / coupon 锁券 / payment 创建支付单）、支付回写（payment → order markPaid）、聚合层（portal/admin）与网关联调
- **order↔seckill 双通道（Feign + Dubbo 3）**：秒杀资格核验 / 预扣默认走 Feign，`mall.seckill.remote=dubbo` 切换 Dubbo RPC（契约 mall-dubbo-api，双通道压测对比）；这是全链路唯一 Dubbo 连接
- **异步解耦**：RocketMQ（延迟消息关单、支付结果发积分、退款四路联动、秒杀削峰）
- **认证链路**：网关全局过滤器调 `mall-auth` 校验 JWT 后放行

演进路线（两阶段，已全部落地）：

1. 全部 OpenFeign 走通全链路（第一阶段）
2. order↔seckill 增加 Dubbo 3 双通道压测对比（第二阶段）；其余链路保持 Feign（全量切 Dubbo 为可选扩展）

> 双协议共存：核心链路服务（product/coupon/payment/seckill/order）同时暴露 HTTP 与 Dubbo（本地 20881～20891 递增，容器内统一 20880）；当前仅 order↔seckill 启用 Dubbo 调用（`mall.seckill.remote` 切换，默认 feign），其余服务 Dubbo 端口预留；聚合层（portal/admin）与网关纯 HTTP 不暴露 Dubbo；注册中心统一 Nacos；Sentinel/SkyWalking 均支持两种协议。

## 分布式事务策略

- **普通业务链路**（下单扣库存、扣优惠券）：Seata AT 模式，`@GlobalTransactional` 注解声明，框架自动反向 SQL 回滚
- **秒杀链路**（Redis 预扣 + MQ 削峰 + 异步落单）：最终一致性——Redis 预扣 + MQ 异步下单 + 关单/对账补偿，不引入全局事务（TCC 两阶段与 MQ 异步链路时序不匹配，本链路不采用；如需演示 TCC，可在普通链路单独搭建对比用例）
- **进阶对比**：RocketMQ 事务消息（半消息 + 回查）实现"本地事务 + 消息"原子性

## 日志方案

- **门面 + 实现**：SLF4J 门面 + Logback 实现（Spring Boot 4 内置 `spring-boot-starter-logging`，版本 1.5.x 由 Boot BOM 管理，无需额外依赖）；代码统一 `@Slf4j` 注解，不引入 log4j2 避免门面冲突
- **输出策略**：开发期控制台；运行期滚动文件（INFO / ERROR 分离，按天滚动 + 保留 15 天，各服务独立日志文件）
- **日志目录策略（三形态）**：默认相对工作目录 `logs/{应用名}/`，可用 `logging.file.path`（Spring Boot 自动映射为 LOG_PATH）或 `-DLOG_PATH` 重定向——IDEA 各模块启动（工作目录 = 模块目录）时日志落在各模块内 `logs/`，各模块各自负责；命令行从根目录批量启动时统一落在根目录 `logs/`；服务器部署建议显式指定绝对路径集中收集。日志文件不入库（.gitignore 已忽略 `*.log` / `*.err` / `logs/`）
- **链路串联（核心）**：网关生成 traceId（无则 UUID）→ 存入 MDC → Feign 经 `X-Trace-Id` 请求头、Dubbo 经 RpcContext attachment、MQ 经消息 properties 透传 → 下游过滤器取出写回 MDC → 日志 pattern 统一携带 `[traceId:%X{traceId}]`——跨服务排查时 grep 同一 traceId 即可串起整条调用链
- **异步注意**：线程池 / MQ 消费线程不继承父线程 MDC，提交任务时需手动透传 traceId（mall-common 提供包装工具，阶段 1 落地）
- **与 SkyWalking 互补**：SkyWalking（阶段 8 javaagent 接入）负责调用链可视化与性能分析，应用日志负责业务明细，两者经 traceId 关联互不替代
- **脱敏**：手机号 / 密码 / token 不打印全量内容

### （可选）SkyWalking 链路追踪接入（阶段 8 13.6）

> javaagent 无侵入：不是 pom 依赖，是 JVM 启动参数挂载的 agent jar（连依赖都不是）。仅演示链路可视化，不接入不影响业务运行。

1. **启动 SkyWalking 服务端**：`docker compose --profile trace up -d`（OAP 11800 / UI 9090）
2. **下载 agent**：SkyWalking 官方下载页解压后得到 `agent/skywalking-agent.jar`（agent 包随发布包自带，无需单独构建）
3. **IDEA 接入**：各服务运行配置 `VM options` 添加一行（`service_name` 按模块名填，如 mall-order / mall-search）：

   ```
   -javaagent:D:/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=mall-order -Dskywalking.collector.backend_service=127.0.0.1:11800
   ```

4. **命令行接入**（Spring Boot 插件透传 JVM 参数）：

   ```powershell
   mvn -pl mall-order spring-boot:run "-Dspring-boot.run.jvmArguments=-javaagent:D:/skywalking/agent/skywalking-agent.jar -Dskywalking.agent.service_name=mall-order -Dskywalking.collector.backend_service=127.0.0.1:11800"
   ```

5. **验证**：浏览器打开 http://localhost:9090（SkyWalking UI），发起任意业务请求（如搜索 / 下单 / 秒杀），「拓扑图」页可看到服务间调用关系，「追踪」页可按 traceId 查看跨服务调用链与各段耗时。

