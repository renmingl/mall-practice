> 本文档为 mall-practice 项目 README 拆分出的专题说明，返回 [README](../README.md)。


> 9 个中间件 = 12 个容器（RocketMQ 3 容器：rocketmq-namesrv / rocketmq-broker / rocketmq-dashboard；SkyWalking 2 容器：skywalking-oap / skywalking-ui；MySQL / Redis / Nacos / Seata / Elasticsearch / Canal / XXL-Job 各 1 容器）不用本机安装：**装了 Docker Desktop 由第 3 步按需启动（默认只跑 Nacos/MySQL/Redis 三个基础件，其余按学习阶段用 `--profile` 拉起）；没装 Docker 则需自行下载 9 个中间件的对应系统版本逐个安装配置**（均有各平台版，但较繁琐，且无法模拟多实例部署）。本机已安装哪个服务，记得从 docker/docker-compose.yml 删除对应的服务段（避免端口冲突，规则详见「环境准备详解」）。
>
> 图片上传为配置驱动双通道：`mall.product.oss.enabled=true` 时上传阿里云 OSS，未配置（默认 false）走本地文件存储，功能照常可用；ODPS 学习阶段不建议接入——两者均可跳过（详见「环境准备详解」）。

## 环境准备详解（选读）

> 「快速开始」第 1 步已覆盖最小安装集；本节能回答"为什么"，并提供完整版本表与可选组件说明。

### 必须安装

| 组件 | 版本 | 安装方式 |
|---|---|---|
| JDK | 17 | 必须本机安装（见下方说明） |
| Maven | 3.9.x（推荐 3.9.16） | 必须本机安装：IDEA 自带 Bundled 3.9.x 可直接选用，或独立安装 3.9.16（最低要求 3.6.3，见下方说明） |
| MySQL | 8.3 | 本机安装 或 Docker 容器版（二选一） |
| Redis | 7.2 | 本机安装 或 Docker 容器版（二选一） |
| Nacos | 3.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| RocketMQ | 5.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| Seata | 2.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| Elasticsearch | 8.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| Canal | 1.1.7 | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| XXL-Job | 3.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |
| SkyWalking | 10.x | 本机安装 或 `docker compose up -d` 容器版（二选一） |

> **JDK 为什么必须本机安装**：IDEA 编译、Maven 打包、单元测试、断点调试都直接调用本机 JDK，Docker 无法替代开发工具链；Docker 中的 `eclipse-temurin:17-jre` 镜像只承载部署期的运行环境。
>
> **Maven 版本要求**：最低 3.6.3（3.6.0 无法运行新版 maven-compiler-plugin，详见“搭建踩坑记录”）。建议直接用 IDEA 自带 Bundled Maven（3.9.x），或独立安装 3.9.16（当前 3.9 线最新正式版）并配置到 IDEA / PATH；暂不推荐 3.10.x / 4.0（均处 RC 阶段，官方标注不建议正式使用）。
>
> **中间件 9 个组件（12 容器）的安装方式**：本机安装（对应系统版）与 Docker 容器版二选一。推荐 Docker——按需启动、版本统一、免手工配置。
>
> **按需删除规则**：本机已安装哪个服务，就从 docker/docker-compose.yml 删除对应的服务段（避免端口冲突）。仓库中的该文件为按需启动版（默认 Nacos/MySQL/Redis 三个基础件，其余按 `--profile` 拉起），按本机情况删除后即可使用。

### 强烈推荐：Docker 容器化（三平台分开装）

| 系统 | 安装方式 | 说明 |
|---|---|---|
| Windows | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)（Hyper-V/WSL2 后端） | 需在 BIOS/系统功能中开启虚拟化；作者 16GB 本机即此方案，README 内存实测数据基于此环境 |
| macOS | [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)（Apple Silicon / Intel 两种芯片版） | M 系列芯片选 arm64 版；本项目所选镜像均已提供 arm64 版，个别旧镜像（如 canal）若仅 amd64，可在 compose 服务下加 `platform: linux/amd64` 经 Rosetta 运行 |
| Linux | 原生 Docker Engine + compose 插件（apt/yum 安装，或 `curl -fsSL https://get.docker.com \| sh`） | 无需 Docker Desktop；无 VM 层内存开销最低，同配置比 Windows/mac 更宽松（见「云服务器部署建议」） |

> 安装后验证 `docker --version` 与 `docker compose version`。

> 微服务本身不依赖 Docker（IDEA 直跑即可），但 Docker 承担两件事：
> 1. **中间件一键部署**：MySQL/Redis/Nacos/RocketMQ/Seata/ES/Canal/XXL-Job/SkyWalking 共 9 个组件（12 容器）通过 docker compose 按需启动（默认 3 个基础件，其余 `--profile` 按阶段拉起），无需逐个手动安装（本机已装的可从 yaml 删除对应段）
> 2. **镜像化与多实例**：微服务独立打包镜像、`--scale` 模拟多实例部署（学习分布式负载均衡的关键手段）
>
> 不装 Docker 的代价：9 个组件需手动下载对应系统版本逐个安装配置（均有各平台版，但较繁琐），且无法模拟多实例部署；微服务开发调试不受影响。

### 可选安装（云服务，可后补）

| 组件 | 用途 | 说明 |
|---|---|---|
| 阿里云 OSS | 对象存储（商品图片上传） | 需开通 OSS 并配置 AK/SK；按量付费，学习用量每月几分钱（新用户有免费额度）。**零成本降级**：上传通道由配置驱动——`mall.product.oss.enabled=false`（默认）走本地文件存储，功能照常可用；开通后在 mall-product 的 application.yml 填好 endpoint / accessKeyId / accessKeySecret / bucket 并置 enabled=true 即切换 OSS（可选 domain 填 CDN 域名） |
| 阿里云 ODPS | 离线数仓 | 按量付费，学习阶段不建议接入（数据量小无意义）；仅保留为大数据量演进方向，本项目不实现 |

### 中间件（docker-compose 按需启动）

编排文件与 .env 统一位于仓库 `docker/` 目录（`docker/docker-compose.yml` + `docker/.env` + 各中间件配置文件 canal/instance.properties、rocketmq/broker.conf，compose 自动读取同目录 .env）。以下中间件无需手动安装，docker compose 会自动拉取镜像并部署（首次约 3GB，无需手动 pull）。**默认只启动 Nacos/MySQL/Redis 三个基础件（约 1GB 内存），其余中间件按学习阶段用 `--profile` 按需启动**（命令见「快速开始」第 3 步）。**中间件账密与数据持久化根目录均在 `docker/.env` 中配置**（数据根目录 `DOCKER_DATA_DIR`、账密类变量见 `docker/.env.example` 模板；未配置时 compose 启动会直接报错，首次使用请复制 `docker/.env.example` 为 `docker/.env` 并设置）——MySQL / Redis / Nacos / Elasticsearch / RocketMQ-Broker / Seata 的数据分别持久化到该目录下的 `mysql / redis / nacos / elasticsearch / rocketmq-broker / seata` 子目录，需迁移时只改 `docker/.env` 一处即可：

| 中间件 | 端口 | 用途 |
|---|---|---|
| MySQL 8.3 | 3306 | 核心交易数据（账密在 docker/.env 配置，数据持久化到 ${DOCKER_DATA_DIR}\mysql） |
| Redis 7.2 | 6379 | 缓存/购物车/秒杀预扣库存（账密在 docker/.env 配置，AOF 持久化到 ${DOCKER_DATA_DIR}\redis） |
| Nacos 3.x | 8848 / 9848（控制台 8849） | 注册中心 + 配置中心（Derby 数据持久化） |
| RocketMQ 5.x | 9876 / 10909 / 10911（Dashboard 9081） | 消息队列（Broker 消息存储持久化） |
| Seata 2.x | 7091 / 8091 | 分布式事务（file 模式事务日志持久化） |
| Elasticsearch 8.x | 9200 | 商品搜索（索引数据持久化） |
| Canal 1.1.7 | 11111 | MySQL binlog 增量同步（product_spu 变更 → ES 商品索引；订阅配置 docker/canal/instance.properties，无数据持久化） |
| XXL-Job 3.x | 9080 | 任务调度 |
| SkyWalking 10.x | 11800 / 12800（UI 9090） | 链路追踪 |

> **客户端依赖 vs 服务端**：以上中间件均分为两部分，缺一不可：
> - **客户端**：pom 依赖形式引入代码（如 nacos-discovery starter、rocketmq-spring-boot-starter、seata starter、ES Java Client、xxl-job-core 执行器、canal.client（mall-search 直连 Canal Server 拉 binlog））；SkyWalking 特殊——agent 是 JVM 参数挂载的 jar，连 pom 依赖都不是
> - **服务端**：独立运行的进程，必须本地安装/启动，即 docker-compose 部署的部分
>
> 类比 MySQL：`mysql-connector-j` 是依赖，MySQL 服务器是独立服务。代码里光有客户端依赖、没有服务端进程是连不上的。

> **完整编排文件**：[docker/docker-compose.yml](docker/docker-compose.yml)（与 .env、中间件配置同处 docker/ 目录，内容以仓库文件为准，README 不再复制）。

### 中间件运维常用命令

Docker 容器版中间件的日常启停/排查，均在 `docker/` 目录下执行：

```bash
docker compose ps                            # 查看全部容器状态
docker compose up -d                         # 启动基础中间件（已运行的自动跳过）
docker compose --profile rocketmq up -d      # 启动指定 profile 的中间件
docker compose up -d redis nacos             # 只启动指定服务
docker compose restart mysql                 # 重启指定服务
docker compose logs -f nacos                 # 跟踪某服务日志
docker compose stop elasticsearch            # 停止某服务（不删容器）
docker compose down                          # 停止并删除全部容器（数据在宿主机卷不丢，目录可在 docker/.env 配置）
docker compose up -d --force-recreate nacos  # 修改 yaml 后强制重建某服务
```

