@echo off
REM Mall System - Docker Deployment Script (Windows)
echo ==========================================
echo   个人店铺电商系统 - Docker 部署
echo ==========================================

REM Check Docker availability
docker --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker is not installed
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Docker Compose is not installed
    exit /b 1
)

REM JWT_SECRET 必填，否则后端会拒绝启动
if not exist .env (
    echo ERROR: 缺少 .env 文件，请先执行: copy .env.example .env 并填入 JWT_SECRET
    exit /b 1
)
findstr /r /c:"^JWT_SECRET=.\{32,\}" .env >nul
if errorlevel 1 (
    echo ERROR: .env 中的 JWT_SECRET 未设置或短于 32 字符
    echo        生成方式: openssl rand -base64 48
    exit /b 1
)

echo 正在拉取基础镜像...
docker pull mysql:8.0
docker pull redis:7-alpine
docker pull eclipse-temurin:17-jre-alpine
docker pull maven:3.9.6-eclipse-temurin-17
docker pull node:20-alpine
docker pull nginx:alpine

echo 正在构建并启动服务...
docker compose up -d --build

echo 等待服务启动...
timeout /t 30 /nobreak >nul

echo.
echo ==========================================
echo   服务状态检查
echo ==========================================
docker compose ps

echo.
echo ==========================================
echo   访问地址
echo ==========================================
echo   前端:    http://localhost:3000
echo   后端API: http://localhost:8080
echo   API文档: http://localhost:8080/doc.html
echo   MySQL:   localhost:3307 (root/root)
echo   Redis:   localhost:6379
echo.
