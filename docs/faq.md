> 本文档为mall-practice 项目勘README 拆分出的专题说明，返回 [README](../README.md)。

## 搭建踩坑记录（均已修复，供避坑参考）

| 坑 | 现象 | 解法 |
|---|---|---|
| Spring Cloud Gateway 5.0 starter 改名 | `spring-cloud-starter-gateway` 依赖版本缺失，编译报错 | 2025.1 起 Gateway 拆出独立版本线 5.0.0，starter 拆分为 `spring-cloud-starter-gateway-server-webflux` / `-server-webmvc`；父 pom 需单独 import `spring-cloud-gateway-dependencies` BOM |
| Seata 镜像命名空间迁移 | `seataio/seata-server` 拉取报 denied | 进入 Apache 孵化器后镜像迁移至 `apache/seata-server`（2.1.0 起） |
| Seata 客户端与服务端版本不对齐 | 运行时协议不匹配风险 | SCA 2025.1.0.0 管理的客户端为 2.5.0，服务端镜像已同步使用 `apache/seata-server:2.5.0` |
| MyBatis-Plus 在 Boot 4 下启动失败 | 普通 starter 不适配 Boot 4 | 必须使用 `mybatis-plus-spring-boot4-starter`（3.5.13 起支持 Boot 4，本工程 3.5.17） |
| Maven 3.6.0 编译报插件版本要求错误 | maven-compiler-plugin 3.13.0 要求 Maven ≥ 3.6.3 | 升级 Maven 3.9.16 后使用 3.13.0（旧 Maven 低于 3.6.3 时需降插件到 3.10.1） |
| Dubbo / Knife4j / ES 客户端的 Boot 4 适配未确认 | 引入可能启动失败 | 接口文档已改用 springdoc-openapi 3.1.0（13 服务 doc.html 落地）；Dubbo 3.3.6 已引入（Boot 4 官方未声明适配，默认 Triple 启动正常，dubbo.enabled=false 可降级 Feign）；ES 客户端 8.17.4 已引入（阶段 8 搜索落地）；RocketMQ / Seata / Sentinel 已由 SCA 2025.1.0.0 官方适配 Boot 4（Release Notes：RocketMQ module support Spring Boot 4.0、Sentinel 适配 Jackson 3），无需验证 |


## 常见问题（FAQ）

**Q：前后台登录的默认账号密码是什么？**
A：统一为 `admin / admin123`（后台为超级管理员；前台商城同账密为演示买家，两套账号体系分表互不影响）。种子数据随 `sql/mall.sql` 首次导入自动生效，图形验证码固定 `8888`；双端入口与账号见 README「第 5 步：验证」。

**Q：本机已有 MySQL/Redis 占用 3306/6379，会与容器冲突吗？**
A：docker/docker-compose.yml 采用按需启动（默认 Nacos/MySQL/Redis 三个基础件，其余用 `--profile` 拉起，命令见「快速开始」第 3 步）；若本机已装某服务仍可能冲突，按规则处理：本机已安装哪个服务，就删除对应的服务段（如删除 mysql/redis 段），删除后再 `docker compose up -d`（在 docker/ 目录执行）。

**Q：微服务跑在容器里，连不上本机的 MySQL/Redis？**
A：容器内将连接地址改为 `host.docker.internal`。

**Q：不装 Docker 能跑项目吗？**
A：能。微服务在 IDEA 直跑即可；但 Nacos/RocketMQ/Seata/ES/Canal/XXL-Job/SkyWalking 需手动下载 Windows 版逐个安装，且无法模拟多实例部署，建议安装。

**Q：启动时报内存不足？**
A：Docker Desktop 设置（Resources）中把虚拟机内存调大（16GB 机器建议 4GB，32GB 机器可 8GB）；本地直跑时按需勾选部分服务——16GB 机器全量跑不动，压缩做法见「极限内存压缩方案」。

**Q：为什么容器内端口都是 8080 不冲突？**
A：端口冲突只在两种情况下发生：同一容器内的多进程、以及宿主机映射端口。不同容器之间各自有独立的网络命名空间和 IP，互不影响，所以 12 个容器内都用 8080 可行；宿主机映射端口必须唯一（如 8080→网关、9080→xxl-job）。类比：每栋楼都有 101 房间，房号相同但互不冲突，园区前台的登记册（端口映射）才需要唯一。

---

