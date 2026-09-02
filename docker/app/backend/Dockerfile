# ============================================================
# mall-practice 后端通用镜像模板（12 个微服务共用一份）
# 多阶段构建：Maven 编译 fat jar -> JRE 精简运行
#
# 用法（由 docker-compose.yml 或 CI 调用，无需手敲）：
#   docker build -f docker/app/backend/Dockerfile --build-arg MODULE=mall-order .
#
# 设计要点：
#   1. 多模块依赖：mvn -pl ${MODULE} -am 会连带编译基础模块
#      （mall-common / mall-mbg / mall-api / mall-dubbo-api）
#   2. 分层缓存：先 COPY 全部 pom.xml 触发依赖下载层缓存，
#      pom 未变时改源码只重编译、不重新下载依赖
#   3. 容器内固定监听 8080（端口映射交给 compose）；
#      JVM 内存由环境变量 JAVA_OPTS 注入（compose 已统一设置 -Xmx256m）
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /build

# 1) 先只 COPY 父 pom 与各模块 pom，触发依赖下载层缓存
COPY pom.xml ./
COPY mall-common/pom.xml mall-common/pom.xml
COPY mall-mbg/pom.xml mall-mbg/pom.xml
COPY mall-api/pom.xml mall-api/pom.xml
COPY mall-dubbo-api/pom.xml mall-dubbo-api/pom.xml
COPY mall-gateway/pom.xml mall-gateway/pom.xml
COPY mall-auth/pom.xml mall-auth/pom.xml
COPY mall-admin/pom.xml mall-admin/pom.xml
COPY mall-portal/pom.xml mall-portal/pom.xml
COPY mall-member/pom.xml mall-member/pom.xml
COPY mall-product/pom.xml mall-product/pom.xml
COPY mall-cart/pom.xml mall-cart/pom.xml
COPY mall-order/pom.xml mall-order/pom.xml
COPY mall-payment/pom.xml mall-payment/pom.xml
COPY mall-coupon/pom.xml mall-coupon/pom.xml
COPY mall-seckill/pom.xml mall-seckill/pom.xml
COPY mall-search/pom.xml mall-search/pom.xml
COPY mall-ai/pom.xml mall-ai/pom.xml

# 2) 预下载依赖（部分插件依赖在 package 阶段才解析，失败不中断）
RUN mvn -B -pl ${MODULE} -am dependency:go-offline || true

# 3) COPY 全部源码后正式打包（-am 连带编译依赖的基础模块，-DskipTests 跳过测试）
COPY . .
RUN mvn -B -pl ${MODULE} -am package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre
ARG MODULE
LABEL org.opencontainers.image.title="mall-practice ${MODULE}" \
      org.opencontainers.image.source="https://github.com/renmingl/mall-practice"

# 时区（保证日志时间与宿主机一致；eclipse-temurin 基础镜像需自装 tzdata）
ENV TZ=Asia/Shanghai
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# fat jar（spring-boot repackage 产物），通配避免硬编码版本号
COPY --from=build /build/${MODULE}/target/*.jar app.jar

EXPOSE 8080
# JAVA_OPTS 由 compose/CI 注入（如 -Xms128m -Xmx256m）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
