#!/usr/bin/env bash
# ============================================================
# build-and-run.sh - one-command: fetch git source -> build images -> start all services
# Usage:
#   ./docker/build-and-run.sh [RepoUrl] [Branch]
#   SKIP_FETCH=1 ./docker/build-and-run.sh     # use existing source, skip git fetch
#   SKIP_BUILD=1 ./docker/build-and-run.sh     # skip image build, only start containers
#   WORK_DIR=/path/to/src ./docker/build-and-run.sh
# Only Docker is required on host (JDK/Maven/Node run inside build containers).
# ============================================================
set -euo pipefail

REPO_URL="${1:-https://github.com/renmingl/mall-practice.git}"
BRANCH="${2:-master}"
SKIP_FETCH="${SKIP_FETCH:-0}"
SKIP_BUILD="${SKIP_BUILD:-0}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # docker/ 的上级 = 仓库根
WORK_DIR="${WORK_DIR:-$ROOT/_build-src}"

step() { printf '\033[36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m    %s\033[0m\n' "$*"; }

# 0) Docker 可用性检查
step "Checking Docker..."
docker version --format '{{.Server.Version}}' >/dev/null || { echo "Docker is not available. Please start Docker first."; exit 1; }
ok "Docker OK"

# 1) 拉取/更新源码
if [ "$SKIP_FETCH" != "1" ]; then
  step "Fetching source from $REPO_URL (branch: $BRANCH) into $WORK_DIR"
  if [ -d "$WORK_DIR/.git" ]; then
    git -C "$WORK_DIR" fetch origin "$BRANCH"
    git -C "$WORK_DIR" checkout "$BRANCH"
    git -C "$WORK_DIR" pull --ff-only origin "$BRANCH" || { echo "git pull failed (local changes?). Commit or stash them first."; exit 1; }
  else
    git clone -b "$BRANCH" --single-branch "$REPO_URL" "$WORK_DIR"
  fi
  ok "Source fetched"
else
  step "SKIP_FETCH=1: use existing source at $WORK_DIR"
fi
[ -f "$WORK_DIR/pom.xml" ] || { echo "No pom.xml found in $WORK_DIR (wrong RepoUrl/WorkDir?)"; exit 1; }

# 2) docker/.env 检查（缺失则从模板复制并退出，等用户改好账密再跑）
if [ ! -f "$WORK_DIR/docker/.env" ]; then
  cp "$WORK_DIR/docker/.env.example" "$WORK_DIR/docker/.env"
  echo
  echo "WARNING: docker/.env created from template."
  echo "         Edit it to set real values first:"
  echo "           DOCKER_DATA_DIR (data root dir)"
  echo "           NACOS_AUTH_* / REDIS_PASSWORD / DB_USERNAME / DB_PASSWORD"
  echo "           XXL_JOB_DB_USERNAME / XXL_JOB_DB_PASSWORD"
  echo "         Then rerun this script."
  exit 1
fi
ok "docker/.env found"

# 3) 构建镜像（首次构建需下载 Maven 依赖，耗时较长）
if [ "$SKIP_BUILD" != "1" ]; then
  step "Building docker images (first run downloads Maven/npm dependencies, takes a while)..."
  ( cd "$WORK_DIR/docker" && docker compose -f docker-compose.apps.yml build )
  ok "Images built"
else
  step "SKIP_BUILD=1: use existing images"
fi

# 4) 一键启动：基础中间件 + 业务中间件（RocketMQ/Seata/XXL-Job）+ 全部应用
step "Starting all services (middleware + apps)..."
( cd "$WORK_DIR/docker" && docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d )

ok "Done. Access:"
echo
echo "  Admin  : http://localhost:5173"
echo "  Portal : http://localhost:5174"
echo "  Gateway: http://localhost:8080"
echo "  Nacos  : http://localhost:8849"
echo "  XXL-Job: http://localhost:9080/xxl-job-admin"
echo
echo "NOTE: Search feature needs ES+Canal: cd docker && docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile search up -d"
echo "      Stop everything: cd docker && docker compose -f docker-compose.yml -f docker-compose.apps.yml down"
