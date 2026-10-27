# ===================================================================
# 【仓库内副本】由项目根目录 start-dev.ps1 同步而来
# 与根目录版本唯一的差异：$rootDir 上溯两级，仍指向项目根目录
# 直接运行：powershell -ExecutionPolicy Bypass -File backend\deploy\start-dev.ps1
# 修改启动流程后请同步根目录原文件，避免两份漂移。
# ===================================================================

# ===================================================================
# 校园活动平台 - 一键启动开发环境 (三端 + 中间件)
# ===================================================================

$ErrorActionPreference = 'SilentlyContinue'
# 【仓库内副本】本文件位于 backend/deploy，需上溯两级定位项目根
$rootDir = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  校园活动打卡与积分管理平台 - 一键启动开发环境 (三端 + 中间件)" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

# 0. 检查并启动 Docker 中间件 (MySQL, Redis, RabbitMQ)
Write-Host "[0/3] 检查并启动 Docker 中间件 (MySQL, Redis, RabbitMQ)..." -ForegroundColor Yellow
Set-Location $rootDir
if (-not (Test-Path (Join-Path $rootDir ".env"))) {
    Write-Host "  -> [警告] 未找到 .env；若容器未启动，请复制 .env.example 为 .env 并填写密码" -ForegroundColor Yellow
}
cmd /c "docker compose up -d mysql redis rabbitmq"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  -> [错误] Docker 中间件启动失败 (退出码 $LASTEXITCODE)，请确认 Docker Desktop 已运行且 .env 配置完整" -ForegroundColor Red
    exit 1
}
Write-Host "  -> Docker 中间件容器已检查/启动完成" -ForegroundColor Green
Write-Host ""

# 1. 启动后端服务 (Spring Boot 8080)
Write-Host "[1/3] 正在启动后端服务 (Spring Boot 8080)..." -ForegroundColor Yellow
$jarPath = Join-Path $rootDir "backend\target\campus-activity-platform-1.0.0-SNAPSHOT.jar"

if (Test-Path $jarPath) {
    Write-Host "  -> 检测到已编译 Jar 包，正在极速拉起..." -ForegroundColor Green
    $backendCmd = "chcp 65001 >nul && cd /d `"$rootDir\backend`" && java `"-Dfile.encoding=UTF-8`" -jar target\campus-activity-platform-1.0.0-SNAPSHOT.jar"
} else {
    Write-Host "  -> 未检测到 Jar 包，使用 Maven Wrapper (spring-boot:run) 启动..." -ForegroundColor Yellow
    $backendCmd = "chcp 65001 >nul && cd /d `"$rootDir\backend`" && .\mvnw.cmd spring-boot:run"
}

cmd /c "start `"Campus-Backend-8080`" cmd /k `"$backendCmd`""
Start-Sleep -Seconds 2

# 2. 启动管理端后台 (Vue 3 / Vite - 端口 3000)
Write-Host "[2/3] 正在启动管理端后台 (Vue 3 / Vite - 端口 3000)..." -ForegroundColor Yellow
$adminCmd = "chcp 65001 >nul && cd /d `"$rootDir\admin-web`" && npm run dev"
cmd /c "start `"Campus-Admin-Web-3000`" cmd /k `"$adminCmd`""
Start-Sleep -Seconds 2

# 3. 启动移动端 (Uni-app H5 - 端口 5173)
Write-Host "[3/3] 正在启动移动端 (Uni-app H5 - 端口 5173)..." -ForegroundColor Yellow
$mobileCmd = "chcp 65001 >nul && cd /d `"$rootDir\mobile-app`" && npm run dev:h5"
cmd /c "start `"Campus-Mobile-App-5173`" cmd /k `"$mobileCmd`""

Write-Host ""
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  三端开发服务均已在独立控制台窗口拉起！" -ForegroundColor Green
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  [管理端后台] (Vue 3):      http://localhost:3000" -ForegroundColor White
Write-Host "     - 超级管理员: admin / 123 (系统用户、知识库、全模块)" -ForegroundColor Gray
Write-Host "     - 普通管理员: manager / 123 (活动发布、礼品管理、全业务)" -ForegroundColor Gray
Write-Host "     - 辅导员账号: counselor / 123 (活动、学生档案、核销、打卡)" -ForegroundColor Gray
Write-Host ""
Write-Host "  [移动端Web] (Uni-app):     http://localhost:5173" -ForegroundColor White
Write-Host "     - 测试学生: 20220101 / 123456 (张伟)" -ForegroundColor Gray
Write-Host "     - 榜首学生: 2023010401 / 123456 (张晓明)" -ForegroundColor Gray
Write-Host ""
Write-Host "  [后端接口] (Spring Boot):  http://localhost:8080/api" -ForegroundColor White
Write-Host "     - Swagger / OpenAPI:    http://localhost:8080/api/doc.html" -ForegroundColor Gray
Write-Host ""
Write-Host "  [中间件服务]:" -ForegroundColor White
Write-Host "     - RabbitMQ 控制台:      http://localhost:15672 (guest / guest)" -ForegroundColor Gray
Write-Host "     - MySQL 数据库:         localhost:3306 (root / rootpassword)" -ForegroundColor Gray
Write-Host "     - Redis 缓存:           localhost:6379 (密码: redispassword)" -ForegroundColor Gray
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  提示: 随时可双击运行 stop-dev.bat 一键停止并彻底释放所有占用端口。" -ForegroundColor Yellow
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""
