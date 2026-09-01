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


## （进阶）Docker 独立部署

> 规划中，待业务代码完成后补充。

本地稳定后，每个模块独立打包镜像部署（后端模块 Dockerfile 与 compose 微服务段随业务代码开发阶段补充，当前 docker/docker-compose.yml 仅含中间件编排；前端两模块为静态资源，由各自 Nginx 镜像承载，部署方式见各前端模块 README）：

```bash
# 注意：当前 compose 仅含中间件，微服务镜像构建需先补齐各模块 Dockerfile 与 compose 服务段
mvn package -DskipTests
docker compose up -d --build          # 全量部署
docker compose up -d --scale mall-order=3   # 订单服务 3 副本
docker compose up -d --scale mall-gateway=2 # 网关 2 副本
```

- 每个模块独立 `Dockerfile`（基础镜像 `eclipse-temurin:17-jre`）
- 全部容器加入同一 bridge 网络（如 `mall-net`），服务间用容器名互访
- 容器内访问宿主机中间件（MySQL/Redis）使用 `host.docker.internal`

