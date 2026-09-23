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

## 升级已有环境（数据库迁移）

MySQL 的初始化脚本**只在数据目录为空时执行一次**。所以对一个已经在跑的卷，
`docker-compose up -d` 不会帮你补上后来新增的表结构，必须手工执行迁移：

```bash
docker exec -i mall-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" mall_db' \
    < docker/mysql/migration/001_coupon_chain.sql
```

脚本是幂等的（每条 DDL 前先查 `INFORMATION_SCHEMA`），重复执行无副作用。
全部迁移脚本都在 `docker/mysql/migration/` 下，按文件名顺序执行。
**不要把它们放进 `docker/mysql/init/`** —— 那是权威建库脚本的目录。

执行完请手动校验（迁移路径没有自动化测试覆盖）：

```bash
docker exec mall-mysql mysql -uroot -p -e 'SHOW CREATE TABLE mall_db.t_order\G' 
docker exec mall-mysql mysql -uroot -p -e 'SHOW CREATE TABLE mall_db.t_user_coupon\G'
```

或者干脆重置数据（会清空所有数据，仅限开发环境）：

```bash
docker-compose down -v && docker-compose up -d
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
