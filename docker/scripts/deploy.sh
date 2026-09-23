#!/bin/bash
# Mall System - Docker Deployment Script
# Run this script to build and start all services

set -e

echo "=========================================="
echo "  个人店铺电商系统 - Docker 部署"
echo "=========================================="

# Check Docker availability
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed"
    exit 1
fi

if ! command -v docker compose &> /dev/null; then
    echo "ERROR: Docker Compose is not installed"
    exit 1
fi

# JWT_SECRET 必填，否则后端会拒绝启动
if [ ! -f .env ]; then
    echo "ERROR: 缺少 .env 文件，请先执行：cp .env.example .env 并填入 JWT_SECRET"
    exit 1
fi
if ! grep -q '^JWT_SECRET=.\{32,\}' .env; then
    echo "ERROR: .env 中的 JWT_SECRET 未设置或短于 32 字符"
    echo "       生成方式：openssl rand -base64 48"
    exit 1
fi

# Pull base images（与各 Dockerfile 的 FROM 保持一致）
echo "正在拉取基础镜像..."
docker pull mysql:8.0
docker pull redis:7-alpine
docker pull eclipse-temurin:17-jre-alpine
docker pull maven:3.9.6-eclipse-temurin-17
docker pull node:20-alpine
docker pull nginx:alpine

# Build and start all services
echo "正在构建并启动服务..."
docker compose up -d --build

# Wait for services to be ready
echo "等待服务启动..."
sleep 30

# Check status
echo ""
echo "=========================================="
echo "  服务状态检查"
echo "=========================================="
docker compose ps

echo ""
echo "=========================================="
echo "  访问地址"
echo "=========================================="
echo "  前端:    http://localhost:3000"
echo "  后端API: http://localhost:8080"
echo "  API文档: http://localhost:8080/doc.html"
echo "  MySQL:   localhost:3307 (root/root)"
echo "  Redis:   localhost:6379"
echo ""
