> 本文档为mall-practice 项目勘README 拆分出的专题说明，返回 [README](../README.md)。

## 端口规划总表

### 微服务

| 模块 | HTTP 端口 | Dubbo 端口（本地，预留） | XXL-Job 执行器端口 | 容器内端口（Docker 部署） |
|---|---|---|---|---|
| mall-gateway | 8080 | - | — | 8080 |
| mall-auth | 8100 | 20881 | — | 8080 |
| mall-admin | 8200 | 20882 | — | 8080 |
| mall-portal | 8300 | 20883 | — | 8080 |
| mall-member | 8400 | 20884 | — | 8080 |
| mall-product | 8500 | 20885 | 9704 | 8080 |
| mall-cart | 8600 | 20886 | — | 8080 |
| mall-order | 8700 | 20887 | 9701 | 8080 |
| mall-payment | 8800 | 20888 | 9702 | 8080 |
| mall-coupon | 8900 | 20889 | 9703 | 8080 |
| mall-seckill | 9000 | 20890 | 9705 | 8080 |
| mall-search | 9100 | 20891 | — | 8080 |

> XXL-Job 执行器端口仅接入任务调度的 5 个服务启用（order 9701 / payment 9702 / coupon 9703 / product 9704 / seckill 9705，`xxl.job.executor.port` 配置，任务清单见 sql/xxl_job.sql 种子）；其余服务未引入 xxl-job-core。

### 前端（npm 模块，独立部署）

| 模块 | 开发端口 | 生产部署 |
|---|---|---|
| mall-web-admin 管理后台 | 5173 | Nginx 独立镜像（构建产物 dist/） |
| mall-web-portal 前台商城 | 5174 | Nginx 独立镜像（构建产物 dist/） |

> 开发期由 Vite 代理 `/api` → 网关 8080，前端无需感知后端端口。

### 中间件

| 中间件 | 端口 |
|---|---|
| MySQL（本机复用或容器版） | 3306 |
| Redis（本机复用或容器版） | 6379 |
| Nacos | 8848 / 9848（控制台 8849） |
| RocketMQ | 9876 / 10909 / 10911（Dashboard 9081） |
| Seata | 7091 / 8091 |
| Elasticsearch | 9200 |
| Canal | 11111 |
| XXL-Job | 9080 |
| SkyWalking | 11800 / 12800（UI 9090） |

端口规则：

- **本地直跑**：各服务 HTTP/Dubbo 端口均不同，互不冲突（同一宿主机进程共享端口空间）；Dubbo 端口全部预留——当前仅 order↔seckill 双通道可选启用（`mall.seckill.remote=dubbo` 时使用，其余链路默认 Feign）；前端开发端口 5173/5174 与后端端口无冲突
- **Docker 单机部署**：每个容器独立网络命名空间，容器内统一 8080 互不影响；仅宿主机映射端口需唯一
- **多实例/多主机**：容器内端口可全部相同；`docker compose up -d --scale mall-order=3` 即可模拟多实例，Nacos 控制台可见同名多实例自动负载均衡


## Docker 独立部署（镜像化一键部署）

12 个后端微服务 + 2 个前端已全部支持打镜像（仓库已含 Dockerfile 与 compose 微服务段）：

- 后端：`docker/Dockerfile.backend` 通用多阶段模板（Maven 构建 fat jar → `eclipse-temurin:17-jre` 运行），12 模块共用，`--build-arg MODULE=<模块名>` 区分
- 前端：`docker/Dockerfile.frontend` 通用镜像（Node 构建 → Nginx 托管，同域反代网关），admin/portal 共用，`--build-arg APP_DIR=<前端目录>` 区分
- 镜像命名：`${IMAGE_PREFIX:-mall-practice}/<模块>:${APP_TAG:-latest}`（本地默认 `mall-practice/xxx:latest`）
- 配置：各模块 application.yml 写死的 127.0.0.1 由 compose 环境变量覆盖为容器服务名（Spring Boot 宽松绑定，零代码改动）

### 编排文件：中间件与应用拆分为两个 yaml

docker/ 目录下两个 compose 文件（需在同一目录运行；compose 项目名相同，共享同一默认网络，应用容器可直接用服务名访问中间件）：

| 文件 | 内容 | 镜像来源 |
|---|---|---|
| `docker-compose.yml` | 12 个中间件容器（Nacos/MySQL/Redis/RocketMQ/Seata/ES/Canal/XXL-Job/SkyWalking），profile 按需启动 | Docker Hub 直接拉取 |
| `docker-compose.apps.yml` | 14 个应用（12 微服务 + 2 前端），含环境变量锚点 | 源码构建（`docker compose build`） |

- **合并运行**（一条命令全部启动）：`docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d`
- **分开运行**：先 `docker compose up -d`（中间件，含 profile 按需），再 `docker compose -f docker-compose.apps.yml up -d`（应用，无本地镜像时自动构建）
- **仅构建不启动**：`docker compose -f docker-compose.apps.yml build`
- 应用段不写 `depends_on`（跨文件引用无效），应用启动失败会自动重启直至中间件就绪

### 方式一：一键脚本（适合部署机/全新机器：本机无源码，从 git 拉码→打镜像→启动）

宿主机只需安装 Docker（JDK/Maven/Node 在构建容器内完成，无需本机安装）：

```powershell
# Windows（.cmd 双击或命令行运行）：
.\docker\build-and-run.cmd                        # 默认拉取 GitHub 仓库 master 分支
.\docker\build-and-run.cmd <git地址> <分支>       # 指定仓库/分支（位置参数）
set SKIP_BUILD=1 && .\docker\build-and-run.cmd    # 已有镜像，只启动容器
# Linux/Mac（bash）：
./docker/build-and-run.sh
```

脚本流程：`git clone/pull` → 检查 `docker/.env`（缺失则从模板复制并提示修改）→ `docker compose -f docker-compose.apps.yml build`（构建 14 个应用镜像）→ `docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d`（双文件合并：中间件 + 全部应用）一条命令完成部署。

### 方式二：手动构建部署（本机开发推荐：直接构建当前工作区代码，未提交的改动也会打进镜像，无需 git commit）

```bash
# 在仓库根目录：
# 1. 先准备 docker/.env（复制 .env.example 并按本机修改账密）
# 2. 构建全部应用镜像（首次需下载 Maven/npm 依赖，耗时较长；中间件镜像无需构建，up 时自动拉取）
cd docker && docker compose -f docker-compose.apps.yml build
# 3. 启动：中间件 + 业务中间件（RocketMQ/Seata/XXL-Job）+ 全部应用（双文件合并）
cd docker && docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d
# 4. 扩缩容（微服务独立镜像的优势；应用服务在 apps 文件，需 -f 指定）
docker compose -f docker-compose.apps.yml up -d --scale mall-order=3   # 订单服务 3 副本
```

### 访问地址

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost:5173 |
| 前台商城 | http://localhost:5174 |
| 网关（API 统一入口） | http://localhost:8080 |
| Nacos 控制台 | http://localhost:8849 |
| XXL-Job 控制台 | http://localhost:9080/xxl-job-admin |

### 注意事项

- **内存**：12 个微服务 JVM 已限制 `-Xmx256m`，全开约 4GB；内存紧张可 `docker compose -f docker-compose.apps.yml stop mall-search` 等按需停服
- **搜索功能**：需先启动 ES/Canal：`cd docker && docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile search up -d`
- **xxl-job 初始化**：首次使用需在 MySQL 导入 `sql/xxl_job.sql`（3.1.0 表结构）
- **本机中间件替代**：若使用本机 MySQL/Redis，删除 compose 对应服务段，并把微服务环境变量中的 `mysql:3306`/`redis:6379` 改为 `host.docker.internal:3306`/`host.docker.internal:6379`，同时删除对应 `depends_on` 条目
- **远程镜像仓库**：IMAGE_PREFIX 是切换「本地构建 / CI 远程拉取」两种模式的核心开关（详见 README「镜像命名与 IMAGE_PREFIX」）。CI（GitHub Actions）构建推送到 GHCR 后，任意机器可 `docker compose pull` 拉取运行：
  ```powershell
  $env:IMAGE_PREFIX="ghcr.io/<GitHub用户名>"; cd docker; docker compose -f docker-compose.apps.yml pull; docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d
  ```
- **CI 触发**：push 到 master 后 `.github/workflows/docker-build.yml` 自动构建 14 个镜像并推送 GHCR（tag 为 commit SHA + latest）

