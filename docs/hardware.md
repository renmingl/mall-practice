> 本文档为 mall-practice 项目 README 拆分出的专题说明，返回 [README](../README.md)。

### 硬件配置要求

> 本项目 = 9 个中间件（12 个 Docker 容器，对应关系见下方内存明细表）+ 12 个后端微服务（JVM）+ 2 个前端（Node），**全量同时拉起的峰值内存远超普通单体项目**。先对照下表判断你的机器档位，再决定按哪种方式启动（16GB 机器实测：全量拉起时内存触顶、系统卡死，必须按需分批启动，降载策略见下文）。
>
> **跨平台说明**：本项目 Windows / macOS / Linux 三平台均可运行（代码与配置无任何平台依赖，Docker 安装方式见「环境准备详解」）。作者在 Windows 16GB 本机完成全部开发与验证，本页「实测 / 极限压缩」数据均出自该环境；Linux 原生 Docker 无 VM 层内存开销更低、Apple Silicon 内存效率更高——同配置只会更宽松，放心照做。文中标注「Windows 特有」的内容仅 Windows 用户需要关注，其余平台直接跳过即可。

#### 内存占用明细（按 docker/docker-compose.yml 配置与各服务默认 JVM 估算）

| 组件（9 个中间件 = 12 个容器） | JVM/堆配置 | 实际占用估算 | 说明 |
|---|---|---|---|
| Nacos（注册/配置中心）<br>　└─ nacos | 256MB | ≈0.5GB | 3.x 含内嵌 Derby |
| MySQL 8.3<br>　└─ mysql | 默认 | ≈0.5GB | 容器默认缓冲池 |
| Redis 7.2（AOF 持久化）<br>　└─ redis | — | ≈0.2GB | |
| RocketMQ<br>　├─ rocketmq-namesrv | 256MB | ≈0.4GB | |
| 　├─ rocketmq-broker | 512MB | ≈0.8GB | commitlog 页缓存占用偏高 |
| 　└─ rocketmq-dashboard | 256MB | ≈0.4GB | |
| Seata（事务协调器）<br>　└─ seata | 256MB | ≈0.5GB | |
| Elasticsearch（search profile）<br>　└─ elasticsearch | 512MB | ≈1GB | mmap 占用高 |
| XXL-Job（task profile）<br>　└─ xxl-job | 256MB | ≈0.4GB | |
| Canal（search profile）<br>　└─ canal-server | 256MB | ≈0.3GB | 随 search profile 与 ES 一起拉起（无数据持久化） |
| SkyWalking（trace profile）<br>　├─ skywalking-oap | 512MB | ≈1GB | |
| 　└─ skywalking-ui | 256MB | ≈0.2GB | |
| **中间件小计** | — | **≈6.2GB** | 9 个中间件展开 12 容器：RocketMQ 3 + SkyWalking 2 + 其余 7 个各 1 |
| 后端微服务 ×10 核心（gateway/auth/admin/portal/member/product/cart/order/payment/coupon；另 seckill/search 随阶段 7/8 启用） | 默认堆 | ≈4～5.5GB | 单个常驻 350～550MB |
| 前端 Vite dev ×2（mall-web-admin / mall-web-portal） | — | ≈1GB | 含依赖预构建 |
| 开发工具（IDEA + 浏览器） | — | ≈3～5GB | |
| Windows 系统 + Docker Desktop 引擎 | — | ≈3～4GB | |
| **核心链路总计**（Nacos/MySQL/Redis + RocketMQ 3 容器 + Seata 共 7 容器约 3.3GB + 后端 10 个 + 前端 2 个，不含 IDE） | — | **≈8～10GB** | 16GB 起步档按此口径评估 |
| **全量总计**（12 容器 6.2GB + 后端 12 个 + 前端 2 个 + IDE + Windows/Docker 引擎） | — | **≈15～20GB+** | 需 32GB 及以上（16GB 实测触顶卡死） |

#### 配置分档

| 档位 | 内存 | CPU | 磁盘 | 可运行范围 |
|---|---|---|---|---|
| 最低档 | 16GB | 8 核 | 100GB SSD | 基础中间件 3 件套（Nacos/MySQL/Redis）+ 4～6 个后端 + 1 个前端，其余按阶段分批启动 |
| 推荐档 | 32GB | 8～16 核 | 200GB+ SSD | 全量中间件 + 12 个后端 + 2 个前端 + IDEA，顺畅运行 |
| 顶配档 | 64GB+ | 16 核+ | 500GB+ SSD | 推荐档基础上支持 `--scale` 多实例 + JMeter 压测演练 |

> 16GB 实测结论：Docker 全量中间件 + 全部后端服务 + 双前端 + IDEA 同时拉起时，内存占用持续触顶、系统无响应；**16GB 必须按需启动，32GB 才可全量顺畅**。

#### 低配机器降载策略

1. **中间件按 profile 分批**：默认只启 Nacos/MySQL/Redis（约 1GB，见「第 3 步：启动中间件」），学到哪个阶段再启对应 profile，用完 `docker compose stop` 停掉
2. **后端按阶段只启所需服务**：按学习阶段只启动 4～6 个核心服务（见「第 4 步：启动微服务」），阶段完成后停掉再启下一阶段
3. **调小后端 JVM**：低配机器可统一加 `-Xms256m -Xmx256m -XX:MaxMetaspaceSize=256m`（IDEA VM options 或启动脚本），10 个服务可省约 1.5GB
4. **Docker Desktop 内存上限调低**：Settings → Resources → Memory 设为 4GB（默认 3 件套约 1GB 足够）
5. **避开构建峰值**：Maven 多模块打包 / npm install 时内存峰值高，避免与全量运行同时进行
6. **前端一次只开一个**：骨架验证只需 mall-web-admin，前台商城按需再启

> 以上为通用策略；16GB 机器实测的极限压缩做法（JVM 参数 / 各手段收益）见「极限内存压缩方案」。

#### 云服务器部署建议（以阿里云为例）

本机内存不够时，可把整套系统拆到云服务器上（Linux 原生 Docker，无 Windows/Docker Desktop 额外开销，同配置比本机更能跑）。**最常见的做法是分开部署：一台机器只跑 Docker 中间件、另一台只跑 Java 应用**，两者互不干扰、故障面更小。部署形态与本地一致：docker-compose 起中间件、java -jar 起 12 个后端、Nginx 托管两个前端 dist 并反代 `/api` 到网关 8080。

| 部署形态 | 机器 | 规格建议（阿里云 ECS） | 内存估算依据 | 适用场景 |
|---|---|---|---|---|
| 单机全量 | 1 台 | 8C32G（ecs.g7.2xlarge） | 全量中间件约 6GB + 12 个后端 5～6.5GB + 前端/Nginx 约 1GB + 系统 2～3GB ≈ 峰值 15GB | 最省，学习/演示 |
| 双机分离（最常见） | 2 台 | 8C16G（ecs.c7.2xlarge）×2 | Docker 机：全量中间件 6GB + 系统 2～3GB ≈ 9GB；应用机：12 个后端 5～6.5GB + 前端 1GB + 系统 2GB ≈ 9.5GB | Docker 与 Java 各占一机，互不干扰 |
| 三机生产雏形 | 3 台 | 4C8G（ecs.c7.xlarge）+ 8C16G ×2 | 数据库机：MySQL/Redis ≈ 1.5GB；中间件机：其余中间件 ≈ 7.5GB；应用机：12 个后端 + 前端 ≈ 9.5GB | 数据库独立，贴近生产拓扑 |

只跑部分组件时的起步配置：

- **只跑基础中间件**（Nacos/MySQL/Redis，约 1.2GB）：Docker 机 4C8G 即可
- **只跑当前阶段后端**（4～6 个服务，约 3GB）：应用机 4C8G 起步
- **全量中间件**（12 个容器约 6GB）或 **全量 12 个后端**（约 6.5GB）：分别需要 8C16G

补充说明：

- **磁盘**：系统盘 40GB + 数据盘 100GB ESSD 起步（镜像约 3GB，MySQL/RocketMQ 数据卷与日志持续增长）
- **带宽**：学习调试 3～5Mbps 按量计费即可；对外演示/团队共用建议 10Mbps 起
- **上云改动点**：各模块 application.yml 的 nacos/redis/数据库地址改为中间件机内网 IP；服务间 Feign/Dubbo 走 Nacos 注册自动发现，跨机部署无需改端口；安全组只需放行对外端口（8080 网关入口、8849/9081/9080/9090 各中间件控制台，按需）
- **云服务器日志**：建议各服务统一 `-DLOG_PATH=/opt/mall/logs`（或 application.yml 配 `logging.file.path`）集中到固定目录，便于采集（详见「日志方案」）

### 极限内存压缩方案

> 16GB 机器跑全阶段集成验证的极限压缩实践（2026-08 实测）。背景：本项目全量拉起内存触顶，必须压缩才能完成阶段 4/5/6 验证；按本方案组合压缩后，阶段4 验证仅需约 3.5GB（系统占用 90%+ → 70% 左右）。

#### 实测配置与占用（16GB 机器）

| 组件 | 压缩配置 | 实测占用 |
|---|---|---|
| 后端微服务 ×10 | `-Xms128m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -Xss512k -XX:MaxDirectMemorySize=128m -XX:+UseSerialGC` | 合计 ≈3.2GB（单服务 210～430MB） |
| Nacos | JVM 256m（compose `JVM_XMS/JVM_XMX`） | 753MB（3.x 含 Derby/堆外） |
| RocketMQ Broker | `-Xms512m -Xmx512m`（compose `JAVA_OPT_EXT`） | 835MB（commitlog 页缓存不吃堆） |
| RocketMQ Namesrv | `-Xms256m -Xmx256m` | 284MB |
| RocketMQ Dashboard | `-Xms256m -Xmx256m`（compose `JAVA_OPTS`） | 未参与阶段4/5/6 验证（随 MQ 一起停，约省 0.4GB） |
| Seata | JVM 256m（compose `JVM_XMS/JVM_XMX`） | 383MB |
| MySQL | 默认 | 486MB |
| Redis | 默认 | 21MB |
| Elasticsearch（search profile） | `ES_JAVA_OPTS=-Xms512m -Xmx512m` | 未参与验证，阶段 8 才拉起（明细表 ≈1GB） |
| Canal（search profile） | 默认（镜像内置） | 未参与验证，随 search profile 拉起（≈0.3GB） |
| XXL-Job（task profile） | `-Xms256m -Xmx256m` | 阶段 5 起按需拉起（关单扫描用，≈0.4GB） |
| SkyWalking OAP/UI（trace profile） | 512m / 256m | 未参与验证，阶段 8 按需拉起（≈1.2GB） |
| 前端 Vite dev ×2（admin / portal） | — | ≈1GB（骨架验证只开 admin） |
| seckill / search 服务 | 同 ×10 JVM 档 | 阶段 7/8 才启用（2 个约 0.7～1GB） |
| Docker Desktop VM | Hyper-V 分配 4GB（CPU 6） | 容器共享上限 |

#### 压缩手段（按收益排序）

| 优先级 | 手段 | 可节省 | 做法 |
|---|---|---|---|
| 1 | 按阶段只启所需服务 | ≈1.1GB | 阶段4：gateway/auth/member/product/cart/coupon/portal 7 个；阶段5：+order；阶段6：全 10 个（order/payment/admin 约 1.1GB） |
| 2 | 非验证期停 MQ/Seata | ≈1.5GB | 阶段4 用不到：`docker compose stop rocketmq-namesrv rocketmq-broker seata`（三容器实测约 1.5GB） |
| 3 | JVM 激进档（仅功能验证，勿压测） | ≈0.4GB | `-Xms128m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:MaxDirectMemorySize=96m -XX:+UseSerialGC`（当前验证已用 256m 档，启动脚本为作者本机维护未入库） |
| 4 | 中间件 JVM 再压（改 compose 后 `docker compose up -d --force-recreate`） | ≈0.3GB | nacos/seata 256m→192m、namesrv 256m→128m、broker 512m→384m（broker 大头是页缓存不吃堆，收益有限）；MySQL/Redis 不建议动 |
| 5 | 验证时不开 IDEA（脚本直接起 jar） | ≈2GB | 启动/停止脚本为作者 Windows 本机维护未入库（PowerShell，其他平台手动执行等价命令即可） |

> 组合效果：阶段4 验证采用 1+2 后，java 约 2.2GB + 容器约 1.3GB ≈ 3.5GB。当前验证脚本为省事统一启动了 10 个服务，按需裁剪参考上表。

#### 启动方式经验（仅 Windows 本机特有；Linux/mac 无此问题，可跳过）

- 本机存在周期性向共享控制台进程发送 Ctrl+C 的机制：`Start-Process` / `schtasks` 启动的 java 会在 1～5 分钟内被杀（错误日志内容为 `^C`）
- **解法：Windows 服务方式启动（Session 0 无控制台）可完全免疫**：`sc create MallXxxSvc binPath= "cmd /c <cmd文件>"` + `sc start`（服务显示 Stopped / 1053 属预期，java 进程实际正常运行）；生成器脚本为作者本机维护未入库
- 一键停止全部（杀 java + `docker compose down`，数据卷保留）：脚本同样为作者本机维护未入库

