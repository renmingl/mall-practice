# ============================================================
# mall-practice 前端通用镜像（管理后台 / 前台商城共用一份）：
#   Node 构建 -> Nginx 托管；--build-arg APP_DIR=<前端目录> 区分模块
# 公共镜像放 docker/app/frontend 目录（文件名须为 Dockerfile 供 IDEA 识别），admin/portal 共用
# 构建上下文 = 仓库根（与 docker/app/backend/Dockerfile 同一模式），
#   由 docker compose build（docker-compose.apps.yml）或 CI 调用
# 生产访问：admin http://localhost:5173 / portal http://localhost:5174（80 映射）
# 同域反代：/api -> 网关 mall-gateway:8080，/uploads -> 商品服务 mall-product:8080
# 用法示例：docker build -f docker/app/frontend/Dockerfile --build-arg APP_DIR=mall-web-admin .
# 注意：COPY 源路径相对构建上下文（仓库根），docker/ 内文件须写 docker/ 前缀（如 docker/app/frontend/nginx.conf）
# ============================================================

# ---------- 阶段一：构建（npm ci + vite build） ----------
FROM node:20-alpine AS build
ARG APP_DIR
WORKDIR /app
# 先 COPY 锁文件触发 npm 依赖层缓存（源码改动不重装依赖）
COPY ${APP_DIR}/package.json ${APP_DIR}/package-lock.json ./
RUN npm ci
COPY ${APP_DIR}/ ./
RUN npm run build

# ---------- 阶段二：Nginx 托管 dist ----------
FROM nginx:alpine
COPY docker/app/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
