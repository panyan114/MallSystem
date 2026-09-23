# Docker 部署指南

## 前提条件
- Docker >= 20.10
- Docker Compose >= 2.0

## 快速部署

### 1. 准备环境变量（必填）

```bash
cp .env.example .env
# 生成一个密钥填进 .env 的 JWT_SECRET
openssl rand -base64 48
```

`JWT_SECRET` 未设置时 `docker-compose up` 会直接报错退出（这是刻意设计的，避免用弱密钥启动）。

### 2. 一键启动所有服务
```bash
docker-compose up -d
```

首次启动时 MySQL 容器会自动执行 `docker/mysql/init/init.sql` 建库并灌入测试数据。

### 3. 查看服务状态
```bash
docker-compose ps
```

### 4. 查看日志
```bash
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f mysql
```

### 5. 停止服务
```bash
docker-compose down
```

### 6. 停止并删除数据（重置数据库）
```bash
docker-compose down -v
```

## 访问地址

| 入口 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:3000 | 对应容器内 80 端口 |
| 后端 API | http://localhost:8080 | |
| API 文档 | http://localhost:8080/doc.html | **需直连后端端口**，前端容器的 Nginx 未代理 `/doc/` |
| MySQL | localhost:3307 | 宿主机端口由 `MYSQL_PORT` 控制，容器内始终是 3306 |
| Redis | localhost:6379 | |

## 自定义配置

编辑 `.env` 文件可修改端口和密码：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `JWT_SECRET` | 无（**必填**） | JWT 签名密钥，至少 32 字符 |
| `MYSQL_ROOT_PASSWORD` | `root` | MySQL root 密码 |
| `MYSQL_DATABASE` | `mall_db` | 数据库名 |
| `MYSQL_PORT` | `3307` | MySQL 宿主机映射端口 |
| `REDIS_PORT` | `6379` | Redis 宿主机映射端口 |
| `BACKEND_PORT` | `8080` | 后端宿主机端口 |
| `FRONTEND_PORT` | `3000` | 前端宿主机端口 |

> `.env` 已被 `.gitignore` 排除，不要提交到版本库。

## 单独构建某个服务
```bash
# 仅构建后端
docker-compose build backend

# 仅构建前端
docker-compose build frontend
```

## 进入容器调试
```bash
docker exec -it mall-backend sh
docker exec -it mall-mysql mysql -u root -p mall_db
```
