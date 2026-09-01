@echo off
setlocal
REM ============================================================
REM build-and-run.cmd - one-command: fetch git source -> build images -> start all services
REM Usage:
REM   build-and-run.cmd [RepoUrl] [Branch]
REM   set SKIP_FETCH=1 && build-and-run.cmd     use existing source, skip git fetch
REM   set SKIP_BUILD=1 && build-and-run.cmd     skip image build, only start containers
REM   set WORK_DIR=C:\path\to\src && build-and-run.cmd
REM Only Docker is required on host (JDK/Maven/Node run inside build containers).
REM ============================================================

set "REPO_URL=%~1"
if "%REPO_URL%"=="" set "REPO_URL=https://github.com/renmingl/mall-practice.git"
set "BRANCH=%~2"
if "%BRANCH%"=="" set "BRANCH=master"
if "%SKIP_FETCH%"=="" set "SKIP_FETCH=0"
if "%SKIP_BUILD%"=="" set "SKIP_BUILD=0"

REM ROOT = parent of this script dir (docker/ -> repo root), absolute path
set "ROOT=%~dp0.."
for %%i in ("%ROOT%") do set "ROOT=%%~fi"
if "%WORK_DIR%"=="" set "WORK_DIR=%ROOT%\_build-src"

REM 0) Docker availability check
echo ==^> Checking Docker...
docker version --format "{{.Server.Version}}" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker is not available. Please start Docker Desktop first.
  exit /b 1
)
echo     Docker OK

REM 1) Fetch / update source
if not "%SKIP_FETCH%"=="1" (
  echo ==^> Fetching source from %REPO_URL% (branch: %BRANCH%) into %WORK_DIR%
  if exist "%WORK_DIR%\.git" (
    git -C "%WORK_DIR%" fetch origin %BRANCH%
    git -C "%WORK_DIR%" checkout %BRANCH%
    git -C "%WORK_DIR%" pull --ff-only origin %BRANCH%
    if errorlevel 1 (
      echo [ERROR] git pull failed (local changes?). Commit or stash them first.
      exit /b 1
    )
  ) else (
    git clone -b %BRANCH% --single-branch %REPO_URL% "%WORK_DIR%"
    if errorlevel 1 (
      echo [ERROR] git clone failed.
      exit /b 1
    )
  )
  echo     Source fetched
) else (
  echo ==^> SKIP_FETCH=1: use existing source at %WORK_DIR%
)
if not exist "%WORK_DIR%\pom.xml" (
  echo [ERROR] No pom.xml found in %WORK_DIR% (wrong RepoUrl/WorkDir?)
  exit /b 1
)

REM 2) docker/.env check (copy from template and exit if missing, wait for user to edit passwords)
if not exist "%WORK_DIR%\docker\.env" (
  copy "%WORK_DIR%\docker\.env.example" "%WORK_DIR%\docker\.env" >nul
  echo.
  echo WARNING: docker/.env created from template.
  echo          Edit it to set real values first:
  echo            DOCKER_DATA_DIR (data root dir)
  echo            NACOS_AUTH_* / REDIS_PASSWORD / DB_USERNAME / DB_PASSWORD
  echo            XXL_JOB_DB_USERNAME / XXL_JOB_DB_PASSWORD
  echo          Then rerun this script.
  exit /b 1
)
echo     docker/.env found

REM 3) Build images (first run downloads Maven/npm dependencies, takes a while)
if not "%SKIP_BUILD%"=="1" (
  echo ==^> Building docker images (first run downloads Maven/npm dependencies, takes a while)...
  pushd "%WORK_DIR%\docker"
  docker compose -f docker-compose.apps.yml build
  if errorlevel 1 (
    popd
    echo [ERROR] docker compose build failed.
    exit /b 1
  )
  popd
  echo     Images built
) else (
  echo ==^> SKIP_BUILD=1: use existing images
)

REM 4) Start all: middleware + business middleware (RocketMQ/Seata/XXL-Job) + all apps
echo ==^> Starting all services (middleware + apps)...
pushd "%WORK_DIR%\docker"
docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile rocketmq --profile seata --profile task up -d
if errorlevel 1 (
  popd
  echo [ERROR] docker compose up failed.
  exit /b 1
)
popd

echo.
echo Done. Access:
echo   Admin  : http://localhost:5173
echo   Portal : http://localhost:5174
echo   Gateway: http://localhost:8080
echo   Nacos  : http://localhost:8849
echo   XXL-Job: http://localhost:9080/xxl-job-admin
echo.
echo NOTE: Search feature needs ES+Canal: cd docker ^&^& docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile search up -d
echo       Stop everything: cd docker ^&^& docker compose -f docker-compose.yml -f docker-compose.apps.yml down

endlocal
